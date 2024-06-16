package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.common.fileSize
import de.danielscholz.fileSync.common.formatAsFileSize
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.persistence.isFolderMarker
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane


fun furtherChecks(
    sourceDir: File,
    targetDir: File,
    sourceChanges: Changes,
    targetChanges: Changes,
    currentFilesSource: CurrentFilesResult,
    currentFilesTarget: CurrentFilesResult,
    syncFilesParams: SyncFilesParams
): Boolean {
    println()
    return intern(sourceDir, targetChanges, currentFilesTarget.files, syncFilesParams) &&
            intern(targetDir, sourceChanges, currentFilesSource.files, syncFilesParams)
}


private fun intern(dir: File, changes: Changes, currentFiles: List<FileEntity>, syncFilesParams: SyncFilesParams): Boolean {

    val addedFiles = changes.added.filter { !it.isFolderMarker }
    val deletedFiles = changes.deleted.filter { !it.isFolderMarker }

    val addedFolders = changes.added.filter { it.isFolderMarker }
    val deletedFolders = changes.deleted.filter { it.isFolderMarker }

    val totalNumberOfFiles = currentFiles.size + addedFiles.size + deletedFiles.size
    val changedNumberOfFiles = addedFiles.size + changes.contentChanged.size + changes.modifiedChanged.size + changes.movedOrRenamed.size + deletedFiles.size

    // does not regard deleted files since they are not deleted but moved to history folder
    val diskspaceNeeded = addedFiles.fileSize() + changes.contentChanged.fileSize()

    val fileStore = Files.getFileStore(dir.toPath())
    val usableSpace = fileStore.usableSpace
    val totalSpace = fileStore.totalSpace

    if (changes.hasChanges()) {
        println(dir.toString())
        val list = mutableListOf<Triple<String, String, String?>>()
        fun p(str: String, str2: Any, str3: Any? = null) {
            list += Triple("  $str: ", str2.toString(), str3?.toString()?.let { " $it" })
        }
        if (addedFiles.isNotEmpty()) {
            p("Files to add", addedFiles.size, "(${addedFiles.fileSize().formatAsFileSize()})")
        }
        if (changes.contentChanged.isNotEmpty()) {
            p("Files to update content", changes.contentChanged.size, "(${changes.contentChanged.fileSize().formatAsFileSize()})")
        }
        if (changes.modifiedChanged.isNotEmpty()) {
            p("Files to update modified", changes.modifiedChanged.size)
        }
        if (changes.movedOrRenamed.any { it.renamed && !it.moved }) {
            p("Files to rename", changes.movedOrRenamed.filter { it.renamed && !it.moved }.size)
        }
        if (changes.movedOrRenamed.any { !it.renamed && it.moved }) {
            p("Files to move", changes.movedOrRenamed.filter { !it.renamed && it.moved }.size)
        }
        if (changes.movedOrRenamed.any { it.renamed && it.moved }) {
            p("Files to rename+move", changes.movedOrRenamed.filter { it.renamed && it.moved }.size)
        }
        if (deletedFiles.isNotEmpty()) {
            p("Files to delete", deletedFiles.size)
        }
        if (addedFolders.isNotEmpty()) {
            p("Folders to create", addedFolders.size)
        }
        if (deletedFolders.isNotEmpty()) {
            p("Folders to delete", deletedFolders.size)
        }
        val max1 = list.maxOf { it.first.length }
        val max2 = list.maxOf { it.second.length }
        list.forEach {
            println("${it.first.padEnd(max1)}${it.second.padStart(max2)}${it.third ?: ""}")
        }
        if (diskspaceNeeded > 0) {
            println("  Diskspace needed:    " + diskspaceNeeded.formatAsFileSize())
            println("  Diskspace available: " + usableSpace.formatAsFileSize())
        }
        println()
    } else {
        println(dir.toString())
        println("  no changes to apply")
        println()
    }

    val minDiskFreeSpaceByPercent = totalSpace / 100 * syncFilesParams.minDiskFreeSpacePercent
    val minDiskFreeSpaceByAbsolute = syncFilesParams.minDiskFreeSpaceMB * 1024L * 1024
    val freeSpaceAfterSync = usableSpace - diskspaceNeeded
    if (freeSpaceAfterSync < minDiskFreeSpaceByPercent || freeSpaceAfterSync < minDiskFreeSpaceByAbsolute) {

        val msg = "Not enough free space on the target medium available.\n" +
                "Required is ${diskspaceNeeded.formatAsFileSize()}, but only ${usableSpace.formatAsFileSize()} is available\n" +
                "(config: ${syncFilesParams.minDiskFreeSpacePercent}% OR ${syncFilesParams.minDiskFreeSpaceMB}MB should be left free).\n" +
                "Sync process not started."

        if (!syncFilesParams.confirmations) {
            println(msg)
            return false
        }
        JOptionPane.showConfirmDialog(null, msg, "Information", JOptionPane.OK_CANCEL_OPTION)
        return false
    }

    val changedPercent = if (totalNumberOfFiles > 0) changedNumberOfFiles * 100 / totalNumberOfFiles else 0

    if (changedPercent >= syncFilesParams.maxChangedFilesWarningPercent && changedNumberOfFiles > syncFilesParams.minAllowedChanges) {

        val msg = "More files were changed or deleted than allowed\n" +
                "(changed: ${changedNumberOfFiles - deletedFiles.size}, deleted: ${deletedFiles.size}. This corresponds to: $changedPercent%). " +
                "Do you want to continue the sync process?"

        if (!syncFilesParams.confirmations) {
            println(msg)
            return false
        }

        if (JOptionPane.YES_OPTION != JOptionPane.showConfirmDialog(null, msg, "Confirmation", JOptionPane.YES_NO_OPTION)) {
            return false
        }
    }
    return true
}