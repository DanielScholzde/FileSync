package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.common.addWithCheck
import de.danielscholz.fileSync.common.removeWithCheck
import de.danielscholz.fileSync.common.replace
import de.danielscholz.fileSync.persistence.isFolderMarker
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES


context(FoldersContext)
fun createActions(
    sourceDir: File,
    targetDir: File,
    sourceChanges: Changes,
    targetChanges: Changes,
    changedDir: String,
    deletedDir: String,
): List<Action> {

    val actions = mutableListOf<Action>()

    // Attention: sourceDir / targetDir may be switched by caller!
    fun Changes.createActions(
        sourceDir: File,
        targetDir: File,
        locationOfChanges: Location
    ) {

        added.forEach {
            if (it.isFolderMarker) {
                actions += Action(it.folderId, "", locationOfChanges, -1) {
                    val targetFile = File(targetDir, it.path())
                    process("create dir", "$targetFile") {
                        targetFile.mkdirs()
                        syncResultFiles.addWithCheck(it)
                        currentFilesTarget.files.addWithCheck(it)
                    }
                }
            } else {
                actions += Action(it.folderId, it.name, locationOfChanges) {
                    val sourceFile = File(sourceDir, it.pathAndName())
                    val targetFile = File(targetDir, it.pathAndName())
                    process("add", "$sourceFile -> $targetFile") {
                        checkIsUnchanged(sourceFile, it)
                        Files.copy(sourceFile.toPath(), targetFile.toPath(), COPY_ATTRIBUTES)
                        syncResultFiles.addWithCheck(it)
                        currentFilesTarget.files.addWithCheck(it)
                    }
                }
            }
        }

        contentChanged.forEach { (from, to) ->
            // pathAndName() must be equals in 'from' and 'to'
            actions += Action(to.folderId, to.name, locationOfChanges) {
                val sourceFile = File(sourceDir, to.pathAndName())
                val targetFile = File(targetDir, to.pathAndName())
                process("copy", "$sourceFile -> $targetFile") {
                    checkIsUnchanged(sourceFile, to)
                    checkIsUnchanged(targetFile, from)
                    val backupFile = File(File(targetDir, changedDir), to.pathAndName())
                    backupFile.parentFile.mkdirs()
                    Files.move(targetFile.toPath(), backupFile.toPath())
                    Files.copy(sourceFile.toPath(), targetFile.toPath(), COPY_ATTRIBUTES)
                    syncResultFiles.replace(to)
                    currentFilesTarget.files.replace(to)
                }
            }
        }

        movedOrRenamed.forEach {
            val (from, to) = it
            actions += Action(to.folderId, to.name, locationOfChanges) {
                val sourceFile = File(targetDir, from.pathAndName())
                val targetFile = File(targetDir, to.pathAndName())
                val action = if (it.moved && it.renamed) "move+rename" else if (it.moved) "move" else "rename"
                process(action, "$sourceFile -> $targetFile") {
                    checkIsUnchanged(sourceFile, to)
                    targetFile.parentFile.mkdirs()
                    Files.move(sourceFile.toPath(), targetFile.toPath())
                    syncResultFiles.removeWithCheck(from)
                    syncResultFiles.addWithCheck(to)
                    currentFilesTarget.files.removeWithCheck(from)
                    currentFilesTarget.files.addWithCheck(to)
                }
            }
        }

        movedAndContentChanged.forEach {
            val (from, to) = it
            actions += Action(to.folderId, to.name, locationOfChanges) {
                val sourceFile = File(sourceDir, to.pathAndName())
                val targetFromFile = File(targetDir, from.pathAndName())
                val targetToFile = File(targetDir, to.pathAndName())
                process("move+copy", "$sourceFile -> $targetToFile") {
                    checkIsUnchanged(sourceFile, to)
                    val backupFile = File(File(targetDir, changedDir), from.pathAndName())
                    backupFile.parentFile.mkdirs()
                    Files.move(targetFromFile.toPath(), backupFile.toPath())
                    Files.copy(sourceFile.toPath(), targetToFile.toPath(), COPY_ATTRIBUTES)
                    syncResultFiles.removeWithCheck(from)
                    syncResultFiles.addWithCheck(to)
                    currentFilesTarget.files.removeWithCheck(from)
                    currentFilesTarget.files.addWithCheck(to)
                }
            }
        }

        deleted.forEach {
            if (it.isFolderMarker) {
                actions += Action(it.folderId, "", locationOfChanges, 100 - foldersCtx.getDepth(it.folderId)) {
                    val toDelete = File(targetDir, it.path())
                    process("delete dir", "$toDelete") {
                        if (toDelete.delete()) {
                            syncResultFiles.removeWithCheck(it)
                            currentFilesTarget.files.removeWithCheck(it)
                        }
                    }
                }
            } else {
                actions += Action(it.folderId, it.name, locationOfChanges) {
                    val toDelete = File(targetDir, it.pathAndName())
                    val backupFile = File(File(targetDir, deletedDir), it.pathAndName())
                    process("delete", "$toDelete") {
                        checkIsUnchanged(toDelete, it)
                        backupFile.parentFile.mkdirs()
                        Files.move(toDelete.toPath(), backupFile.toPath())
                        syncResultFiles.removeWithCheck(it)
                        currentFilesTarget.files.removeWithCheck(it)
                    }
                }
            }
        }

        modifiedChanged.forEach { (from, to) ->
            actions += Action(to.folderId, to.name, locationOfChanges) {
                val sourceFile = File(sourceDir, to.pathAndName())
                val targetFile = File(targetDir, to.pathAndName())
                process("modified attr", "$sourceFile -> $targetFile") {
                    checkIsUnchanged(sourceFile, to)
                    checkIsUnchanged(targetFile, from)
                    targetFile.setLastModified(sourceFile.lastModified()) || throw Exception("set of last modification date failed!")
                    syncResultFiles.replace(to)
                    currentFilesTarget.files.replace(to)
                }
            }
        }
    }

    sourceChanges.createActions(
        sourceDir = sourceDir,
        targetDir = targetDir,
        locationOfChanges = Location.TARGET
    )
    targetChanges.createActions(
        sourceDir = targetDir,
        targetDir = sourceDir,
        locationOfChanges = Location.SOURCE
    )

    return actions
}
