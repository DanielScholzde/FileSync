package de.danielscholz.fileSync.actions

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.SyncFiles.Changes
import de.danielscholz.fileSync.actions.SyncFiles.ContentChanged
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.persistence.File2
import java.io.File
import java.nio.file.Files
import javax.swing.JOptionPane


context(FoldersContext, CaseSensitiveContext)
fun checkAndFix(sourceChanges: Changes, targetChanges: Changes, syncResult: MutableSet<File2>): Boolean {

    val failures = mutableListOf<String>()

    fun Collection<File2>.ifNotEmptyCreateConflicts(detailMsg: String) {
        forEach {
            failures += "${it.pathAndName()} $detailMsg"
        }
    }

    fun Collection<Pair<File2, File2>>.ifNotEmptyCreateConflicts(detailMsg: String, diffExtractor: (File2) -> String) {
        forEach {
            failures += "${it.first.pathAndName()} $detailMsg: ${diffExtractor(it.first)} != ${diffExtractor(it.second)}"
        }
    }

    // fix scenario: same added files in both locations (or changed files with same content)
    equalsBy(pathAndName) {
        (sourceChanges.added + sourceChanges.contentChanged.to() intersect targetChanges.added + targetChanges.contentChanged.to())
            .filter2(HASH_EQ)
            .forEach { pair ->
                val (source, target) = pair
                if (source.modified == target.modified) {
                    syncResult -= source // remove old instance (optional)
                    syncResult += source // add new with changed hash
                    sourceChanges.added -= source
                    sourceChanges.contentChanged -= ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, source) // equals of ContentChanged considers only second property
                    targetChanges.added -= target
                    targetChanges.contentChanged -= ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, target)
                } else {
                    listOf(pair).ifNotEmptyCreateConflicts("changed modification date (not equal) within source and target") {
                        it.modified.toStr()
                    }
                }
            }

        // fix scenario: same deleted files in both locations
        (sourceChanges.deleted intersect targetChanges.deleted)
            .forEach { pair ->
                val (source, target) = pair
                syncResult.removeWithCheck(source)
                sourceChanges.deleted -= source
                targetChanges.deleted -= target
            }

        // fix scenario: same moved files in both locations
        // TODO

        (sourceChanges.contentChanged.to() intersect targetChanges.contentChanged.to())
            .filter2(HASH_NEQ)
            .ifNotEmptyCreateConflicts("modified (with different content) within source and target") {
                "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
            }

        (sourceChanges.attributesChanged intersect targetChanges.attributesChanged)
            .filter2(MODIFIED_NEQ)
            .ifNotEmptyCreateConflicts("changed modification date (not equal) within source and target") {
                it.modified.toStr()
            }
    }


    fun directionalChecks(changed1: Changes, changed2: Changes) {
        equalsBy(pathAndName) {
            (changed1.added intersect changed2.allFilesBeforeSync)
                .filter2(HASH_NEQ or MODIFIED_NEQ)
                .ifNotEmptyCreateConflicts("already exists within target dir (and has different content or modification date)") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (changed1.deleted intersect changed2.contentChanged.to())
                .ifNotEmptyCreateConflicts("deleted source file but changed content within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (changed1.movedOrRenamed.from() - changed2.allFilesBeforeSync)
                .ifNotEmptyCreateConflicts("source of moved file does not exists within target dir")

            (changed1.movedOrRenamed.to() intersect changed2.allFilesBeforeSync)
                .ifNotEmptyCreateConflicts("target of moved file already exists within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }
        }
    }

    directionalChecks(sourceChanges, targetChanges)
    directionalChecks(targetChanges, sourceChanges)

    if (failures.isNotEmpty()) {
        println("Conflicts:\n${failures.joinToString("\n")}")
        return false
    }
    return true
}


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
            println(
                "$dir\n" +
                        "  Free diskspace:          ${usableSpace.formatAsFileSize()}\n" +
                        "  Diskspace needed:        ${diskspaceNeeded.formatAsFileSize()}\n" +
                        "  Files to add:            ${leftPad(changes.added.size, maxLength)} (${changes.added.fileSize().formatAsFileSize()})\n" +
                        "  Files to update content: ${leftPad(changes.contentChanged.size, maxLength)} (${changes.contentChanged.fileSize().formatAsFileSize()})\n" +
                        "  Files to rename:         ${leftPad(changes.movedOrRenamed.filter { it.renamed && !it.moved }.size, maxLength)}\n" +
                        "  Files to move:           ${leftPad(changes.movedOrRenamed.filter { !it.renamed && it.moved }.size, maxLength)}\n" +
                        "  Files to rename+move:    ${leftPad(changes.movedOrRenamed.filter { it.renamed && it.moved }.size, maxLength)}\n" +
                        "  Files to delete:         ${leftPad(changes.deleted.size, maxLength)}\n"
            )
        } else {
            println("$dir\n  no changes to apply")
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