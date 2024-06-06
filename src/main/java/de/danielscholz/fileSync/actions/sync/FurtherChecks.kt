package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.common.fileSize
import de.danielscholz.fileSync.common.formatAsFileSize
import de.danielscholz.fileSync.common.leftPad
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane


fun furtherChecks(sourceDir: File, targetDir: File, sourceChanges: Changes, targetChanges: Changes, syncFilesParams: SyncFilesParams): Boolean {

    fun intern(dir: File, changes: Changes): Boolean {

        val totalNumberOfFiles = changes.allFilesBeforeSync.size + changes.added.size + changes.deleted.size
        val changedNumberOfFiles = changes.added.size + changes.contentChanged.size + changes.attributesChanged.size + changes.movedOrRenamed.size + changes.deleted.size

        // does not regard deleted files since they are not deleted but moved to history folder
        val diskspaceNeeded = changes.added.fileSize() + changes.contentChanged.fileSize()

        val fileStore = Files.getFileStore(dir.toPath())
        val usableSpace = fileStore.usableSpace
        val totalSpace = fileStore.totalSpace

        val maxLength = totalNumberOfFiles.toString().length

        if (changes.hasChanges()) {
            println(dir.toString())
            if (diskspaceNeeded > 0) {
                println("  Free diskspace:           ${usableSpace.formatAsFileSize()}")
                println("  Diskspace needed:         ${diskspaceNeeded.formatAsFileSize()}")
            }
            if (changes.added.isNotEmpty()) {
                println("  Files to add:             ${leftPad(changes.added.size, maxLength)} (${changes.added.fileSize().formatAsFileSize()})")
            }
            if (changes.contentChanged.isNotEmpty()) {
                println("  Files to update content:  ${leftPad(changes.contentChanged.size, maxLength)} (${changes.contentChanged.fileSize().formatAsFileSize()})")
            }
            if (changes.attributesChanged.isNotEmpty()) {
                println("  Files to update modified: ${leftPad(changes.attributesChanged.size, maxLength)}")
            }
            if (changes.movedOrRenamed.any { it.renamed && !it.moved }) {
                println("  Files to rename:          ${leftPad(changes.movedOrRenamed.filter { it.renamed && !it.moved }.size, maxLength)}")
            }
            if (changes.movedOrRenamed.any { !it.renamed && it.moved }) {
                println("  Files to move:            ${leftPad(changes.movedOrRenamed.filter { !it.renamed && it.moved }.size, maxLength)}")
            }
            if (changes.movedOrRenamed.any { it.renamed && it.moved }) {
                println("  Files to rename+move:     ${leftPad(changes.movedOrRenamed.filter { it.renamed && it.moved }.size, maxLength)}")
            }
            if (changes.deleted.isNotEmpty()) {
                println("  Files to delete:          ${leftPad(changes.deleted.size, maxLength)}")
            }
        } else {
            println(dir.toString())
            println("  no changes to apply")
        }

        if (usableSpace - diskspaceNeeded < totalSpace / 100 * syncFilesParams.minDiskFreeSpacePercent
            || usableSpace - diskspaceNeeded < syncFilesParams.minDiskFreeSpaceMB * 1024L * 1024
        ) {

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