package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.Folders
import de.danielscholz.fileSync.common.fileSize
import de.danielscholz.fileSync.common.formatAsFileSize
import de.danielscholz.fileSync.common.ifNotEmpty
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.persistence.isFolderMarker
import de.danielscholz.fileSync.ui.UI
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane


fun furtherChecks(
    sourceDir: File,
    targetDir: File,
    sourceChanges: Changes,
    targetChanges: Changes,
    currentFilesSource: MutableCurrentFiles,
    currentFilesTarget: MutableCurrentFiles,
    syncFilesParams: SyncFilesParams,
    folders: Folders,
): Boolean {
    println()

    val sourceChangesWithDetails = ChangesWithDetails(sourceDir, UI.sourceDir, targetChanges, currentFilesTarget.files, syncFilesParams)
    val targetChangesWithDetails = ChangesWithDetails(targetDir, UI.targetDir, sourceChanges, currentFilesSource.files, syncFilesParams)

    UI.sourceDir.changes = sourceChangesWithDetails
    UI.targetDir.changes = targetChangesWithDetails

    val sourceChecks = FurtherChecks(sourceDir, sourceChangesWithDetails, syncFilesParams)
    val targetChecks = FurtherChecks(targetDir, targetChangesWithDetails, syncFilesParams)

    sourceChecks.printout()
    targetChecks.printout()

    sourceChangesWithDetails.foldersToAdd.forEach {
        println("Folder to create (source): " + it.path(folders))
    }
    targetChangesWithDetails.foldersToAdd.forEach {
        println("Folder to create (target): " + it.path(folders))
    }

    sourceChangesWithDetails.filesToAdd.sortedByDescending { it.size }.ifNotEmpty {
        it.take(10).forEach {
            println("File to add (source): " + it.pathAndName(folders) + " " + it.size.formatAsFileSize())
        }
        if (it.size > 10) println("...")
    }
    targetChangesWithDetails.filesToAdd.sortedByDescending { it.size }.ifNotEmpty {
        it.take(10).forEach {
            println("File to add (target): " + it.pathAndName(folders) + " " + it.size.formatAsFileSize())
        }
        if (it.size > 10) println("...")
    }

    return sourceChecks.check() && targetChecks.check()
}

class ChangesWithDetails(
    private val dir: File,
    private val uiDir: UI.Dir,
    private val changes: Changes,
    private val currentFiles: Set<FileEntity>,
    private val syncFilesParams: SyncFilesParams
) : Changes by changes {

    val filesToAdd = changes.added.filter { !it.isFolderMarker }
    val filesToDelete = changes.deleted.filter { !it.isFolderMarker }

    val foldersToAdd = changes.added.filter { it.isFolderMarker }
    val foldersToDelete = changes.deleted.filter { it.isFolderMarker }

    private val totalNumberOfFiles = currentFiles.size + filesToAdd.size + filesToDelete.size
    val changedNumberOfFiles = 0 +
            filesToAdd.size +
            filesToDelete.size +
            changes.contentChanged.size +
            changes.modifiedChanged.size +
            changes.movedOrRenamed.size +
            changes.movedAndContentChanged.size
    val changedPercent = if (totalNumberOfFiles > 0) changedNumberOfFiles * 100 / totalNumberOfFiles else 0

    val diskspaceNeeded = changes.diskspaceNeeded()

    private val fileStore = Files.getFileStore(dir.toPath())
    private val totalSpace = fileStore.totalSpace
    val usableSpace = fileStore.usableSpace

    val minDiskFreeSpaceByPercent = totalSpace / 100 * syncFilesParams.minDiskFreeSpacePercent
    val minDiskFreeSpaceByAbsolute = syncFilesParams.minDiskFreeSpaceMB * 1024L * 1024
    val freeSpaceAfterSync = usableSpace - diskspaceNeeded

}

class FurtherChecks(
    private val dir: File,
    private val changes: ChangesWithDetails,
    private val syncFilesParams: SyncFilesParams
) {

    fun printout() = with(changes) {
        if (hasChanges()) {
            println(dir.toString())

            val list = mutableListOf<Triple<String, String, String?>>()

            fun p(str: String, str2: Any, str3: Any? = null) {
                list += Triple("  $str: ", str2.toString(), str3?.toString()?.let { " $it" })
            }

//            if (folderRenamed.isNotEmpty()) {
//                p("Folder to rename", folderRenamed.size)
//            }
            if (filesToAdd.isNotEmpty()) {
                p("Files to add", filesToAdd.size, "(${filesToAdd.fileSize().formatAsFileSize()})")
            }
            if (contentChanged.isNotEmpty()) {
                p("Files to update content", contentChanged.size, "(${contentChanged.fileSize().formatAsFileSize()})")
            }
            if (modifiedChanged.isNotEmpty()) {
                p("Files to update modified", modifiedChanged.size)
            }
            if (renamed().isNotEmpty()) {
                p("Files to rename", renamed().size)
            }
            if (moved().isNotEmpty()) {
                p("Files to move", moved().size)
            }
            if (movedAndRenamed().isNotEmpty()) {
                p("Files to rename + move", movedAndRenamed().size)
            }
            if (movedAndContentChanged.isNotEmpty()) {
                p("Files to move + update content", movedAndContentChanged.size)
            }
            if (filesToDelete.isNotEmpty()) {
                p("Files to delete", filesToDelete.size)
            }
            if (foldersToAdd.isNotEmpty()) {
                p("Folders to create", foldersToAdd.size)
            }
            if (foldersToDelete.isNotEmpty()) {
                p("Folders to delete", foldersToDelete.size)
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
            println("  --  (no changes to apply)")
            println()
        }
    }


    fun check(): Boolean = with(changes) {

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

        if (changedPercent >= syncFilesParams.maxChangedFilesWarningPercent && changedNumberOfFiles > syncFilesParams.minAllowedChanges) {

            val msg = "More files were changed or deleted than allowed\n" +
                    "(added: ${filesToAdd.size}, changed: ${changedNumberOfFiles - filesToDelete.size - filesToAdd.size}, deleted: ${filesToDelete.size}. This corresponds to: ${changedPercent}%). " +
                    "Do you want to continue the sync process?" + (if (syncFilesParams.dryRun) " (dry-run)" else "")

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
}