package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.common.addWithCheck
import de.danielscholz.fileSync.common.removeWithCheck
import de.danielscholz.fileSync.common.replace
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

    fun Changes.createActions(sourceDir: File, targetDir: File) {

        added.forEach {
            actions += Action(it.folderId, it.name) {
                val sourceFile = File(sourceDir, it.pathAndName())
                val targetFile = File(targetDir, it.pathAndName())
                process("add", "$sourceFile -> $targetFile") {
                    targetFile.parentFile.mkdirs()
                    Files.copy(sourceFile.toPath(), targetFile.toPath(), COPY_ATTRIBUTES)
                    syncResultFiles.addWithCheck(it)
                }
            }
        }

        contentChanged.forEach { (_, to) ->
            // pathAndName() must be equals in 'from' and 'to'
            actions += Action(to.folderId, to.name) {
                val sourceFile = File(sourceDir, to.pathAndName())
                val targetFile = File(targetDir, to.pathAndName())
                process("copy", "$sourceFile -> $targetFile") {
                    val backupFile = File(File(targetDir, changedDir), to.pathAndName())
                    backupFile.parentFile.mkdirs()
                    Files.move(targetFile.toPath(), backupFile.toPath())
                    Files.copy(sourceFile.toPath(), targetFile.toPath(), COPY_ATTRIBUTES)
                    syncResultFiles.replace(to)
                }
            }
        }

        modifiedChanged.forEach { (_, to) ->
            actions += Action(to.folderId, to.name) {
                val sourceFile = File(sourceDir, to.pathAndName())
                val targetFile = File(targetDir, to.pathAndName())
                process("modified attr", "$sourceFile -> $targetFile") {
                    targetFile.setLastModified(sourceFile.lastModified()) || throw Exception("set of last modification date failed!")
                    syncResultFiles.replace(to)
                }
            }
        }

        movedOrRenamed.forEach {
            val (from, to) = it
            actions += Action(to.folderId, to.name) {
                val sourceFile = File(targetDir, from.pathAndName())
                val targetFile = File(targetDir, to.pathAndName())
                val action = if (it.moved && it.renamed) "move+rename" else if (it.moved) "move" else "rename"
                process(action, "$sourceFile -> $targetFile") {
                    targetFile.parentFile.mkdirs()
                    Files.move(sourceFile.toPath(), targetFile.toPath())
                    syncResultFiles.removeWithCheck(from)
                    syncResultFiles.addWithCheck(to)
                }
            }
        }

        deleted.forEach {
            actions += Action(it.folderId, it.name) {
                val toDelete = File(targetDir, it.pathAndName())
                val backupFile = File(File(targetDir, deletedDir), it.pathAndName())
                process("delete", "$toDelete") {
                    backupFile.parentFile.mkdirs()
                    Files.move(toDelete.toPath(), backupFile.toPath())
                    syncResultFiles.removeWithCheck(it)
                }
            }
        }
    }

    sourceChanges.createActions(sourceDir, targetDir)
    targetChanges.createActions(targetDir, sourceDir)

    return actions
}
