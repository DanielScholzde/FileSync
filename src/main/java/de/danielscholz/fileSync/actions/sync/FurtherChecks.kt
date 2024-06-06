package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.common.fileSize
import de.danielscholz.fileSync.common.formatAsFileSize
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane


fun furtherChecks(sourceDir: File, targetDir: File, sourceChanges: Changes, targetChanges: Changes, syncFilesParams: SyncFilesParams): Boolean {

    fun intern(dir: File, changes: Changes): Boolean {

        val totalNumberOfFiles = changes.allFilesBeforeSync.size + changes.added.size + changes.deleted.size
        val changedNumberOfFiles = changes.added.size + changes.contentChanged.size + changes.modifiedChanged.size + changes.movedOrRenamed.size + changes.deleted.size

        // does not regard deleted files since they are not deleted but moved to history folder
        val diskspaceNeeded = changes.added.fileSize() + changes.contentChanged.fileSize()

        val fileStore = Files.getFileStore(dir.toPath())
        val usableSpace = fileStore.usableSpace
        val totalSpace = fileStore.totalSpace

        if (changes.hasChanges()) {
            println(dir.toString())
            val list = mutableListOf<Triple<String, String, String?>>()
            fun p(str: String, str2: Any, str3: Any? = null) {
                list += Triple("  $str: ", str2.toString(), str3?.toString()?.let { " $it" })
            }
            if (changes.added.isNotEmpty()) {
                p("Files to add", changes.added.size, "(${changes.added.fileSize().formatAsFileSize()})")
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
            if (changes.deleted.isNotEmpty()) {
                p("Files to delete", changes.deleted.size)
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
        } else {
            println(dir.toString())
            println("  no changes to apply")
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
                    "(changed: ${changedNumberOfFiles - changes.deleted.size}, deleted: ${changes.deleted.size}. This corresponds to: $changedPercent%). " +
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

    println()
    return intern(sourceDir, targetChanges) &&
            intern(targetDir, sourceChanges)
}