package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.persistence.isEmptyFile
import de.danielscholz.fileSync.persistence.isFolderMarker
import java.io.File


context(FoldersContext)
fun createActions(sourceChanges: Changes, targetChanges: Changes): List<Action> =
    sourceChanges.createActions(switchedSourceAndTarget = false, locationOfChangesToBeMade = Location.TARGET) +
            targetChanges.createActions(switchedSourceAndTarget = true, locationOfChangesToBeMade = Location.SOURCE)


context(FoldersContext)
private fun Changes.createActions(switchedSourceAndTarget: Boolean, locationOfChangesToBeMade: Location): List<Action> {

    val actions = mutableListOf<Action>()

//    folderRenamed.forEach {(oldFolderId, currentFolderId) ->
//        actions += Action(currentFolderId, "", locationOfChangesToBeMade, switchedSourceAndTarget, -2) {
//            val from = File(targetDir, foldersCtx.getFullPath(oldFolderId))
//            val to = File(targetDir, foldersCtx.getFullPath(currentFolderId))
//            process("rename dir", "$from -> $to") {
//                Files.move(from.toPath(),to.toPath())
////                if () {
////                    throw Exception("folder rename failed $from -> $to")
////                }
////                syncResultFiles.addWithCheck(it)
////                currentFilesTarget.addWithCheck(it)
//            }
//        }
//    }

    added.forEach {
        actions += if (it.isFolderMarker) {
            Action(it.folderId, "", locationOfChangesToBeMade, switchedSourceAndTarget, -1) {
                val directoryToCreate = File(targetDir, it.path())
                processDir("create dir", "$directoryToCreate") {
                    fs.createDirsFor(directoryToCreate)
                    syncResultFilesSource.addWithCheck(it)
                    currentFilesTarget.addWithCheck(it)
                }
            }
        } else {
            Action(it.folderId, it.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
                val sourceFile = File(sourceDir, it.pathAndName())
                val targetFile = File(targetDir, it.pathAndName())
                process("add", "$sourceFile -> $targetFile", it.isEmptyFile) {
                    checkIsUnchanged(sourceFile, it)
                    fs.copy(sourceFile, targetFile, it.fileHash?.hash).also { if (it == FileSystemEncryption.State.ENCRYPTED) encrypted = true }
                    syncResultFilesSource.addWithCheck(it)
                    currentFilesTarget.addWithCheck(it)
                    bytesCopied(it.size)
                }
            }
        }
    }

    contentChanged.forEach { (from, to) ->
        // pathAndName() must be equals in 'from' and 'to'
        actions += Action(to.folderId, to.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
            val sourceFile = File(sourceDir, to.pathAndName())
            val targetFile = File(targetDir, to.pathAndName())
            process("copy", "$sourceFile -> $targetFile", to.isEmptyFile) {
                checkIsUnchanged(sourceFile, to)
                checkIsUnchanged(targetFile, from)
                val backupFile = File(File(targetDir, changedDir), to.pathAndName())
                fs.createDirsFor(backupFile.parentFile)
                fs.move(targetFile, backupFile, from.fileHash?.hash)
                fs.copy(sourceFile, targetFile, to.fileHash?.hash).also { if (it == FileSystemEncryption.State.ENCRYPTED) encrypted = true }
                syncResultFilesSource.replace(to)
                currentFilesTarget.replace(to)
                bytesCopied(to.size)
            }
        }
    }

    movedOrRenamed.forEach {
        val (from, to) = it
        actions += Action(to.folderId, to.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
            val sourceFile = File(targetDir, from.pathAndName())
            val targetFile = File(targetDir, to.pathAndName())
            val action = if (it.moved && it.renamed) "move+rename" else if (it.moved) "move" else "rename"
            process(action, "$sourceFile -> $targetFile", to.isEmptyFile) {
                checkIsUnchanged(sourceFile, to)
                // special case: change in lower/upper case only
                if (it.renamed && !it.moved && from.name != to.name && from.nameLowercase == to.nameLowercase) {
                    val tmpFile = targetFile.resolveSibling(targetFile.name + "__tmp")
                    if (!tmpFile.exists()) {
                        fs.move(sourceFile, tmpFile, from.fileHash?.hash)
                        fs.move(tmpFile, targetFile, from.fileHash?.hash).also { if (it == FileSystemEncryption.State.ENCRYPTED) encrypted = true }
                    } else throw Exception("tmp file already exists!")
                } else {
                    fs.move(sourceFile, targetFile, from.fileHash?.hash).also { if (it == FileSystemEncryption.State.ENCRYPTED) encrypted = true }
                }
                syncResultFilesSource.removeWithCheck(from)
                syncResultFilesSource.addWithCheck(to)
                currentFilesTarget.removeWithCheck(from)
                currentFilesTarget.addWithCheck(to)
            }
        }
    }

    movedAndContentChanged.forEach {
        val (from, to) = it
        actions += Action(to.folderId, to.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
            val sourceFile = File(sourceDir, to.pathAndName())
            val targetFromFile = File(targetDir, from.pathAndName())
            val targetToFile = File(targetDir, to.pathAndName())
            process("copy", "$sourceFile -> $targetToFile", to.isEmptyFile) {
                checkIsUnchanged(sourceFile, to)
                val backupFile = File(File(targetDir, changedDir), from.pathAndName())
                fs.createDirsFor(backupFile.parentFile)
                fs.move(targetFromFile, backupFile, from.fileHash?.hash)
                fs.copy(sourceFile, targetToFile, to.fileHash?.hash).also { if (it == FileSystemEncryption.State.ENCRYPTED) encrypted = true }
                syncResultFilesSource.removeWithCheck(from)
                syncResultFilesSource.addWithCheck(to)
                currentFilesTarget.removeWithCheck(from)
                currentFilesTarget.addWithCheck(to)
                bytesCopied(to.size)
            }
        }
    }

    deleted.forEach {
        actions += if (it.isFolderMarker) {
            Action(it.folderId, "", locationOfChangesToBeMade, switchedSourceAndTarget, 100 - foldersCtx.getDepth(it.folderId)) {
                val directoryToDelete = File(targetDir, it.path())
                processDir("delete dir", "$directoryToDelete") {
                    val file = File(directoryToDelete, "Thumbs.db")
                    if (file.isFile) fs.deleteFile(file)
                    fs.deleteDir(directoryToDelete)
                    syncResultFilesSource.removeWithCheck(it)
                    currentFilesTarget.removeWithCheck(it)
                }
            }
        } else {
            Action(it.folderId, it.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
                val toDelete = File(targetDir, it.pathAndName())
                val backupFile = File(File(targetDir, deletedDir), it.pathAndName())
                process("delete", "$toDelete", it.isEmptyFile) {
                    checkIsUnchanged(toDelete, it)
                    fs.createDirsFor(backupFile.parentFile)
                    fs.move(toDelete, backupFile, it.fileHash?.hash)
                    syncResultFilesSource.removeWithCheck(it)
                    currentFilesTarget.removeWithCheck(it)
                }
            }
        }
    }

    modifiedChanged.forEach { (from, to) ->
        actions += Action(to.folderId, to.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
            val sourceFile = File(sourceDir, to.pathAndName())
            val targetFile = File(targetDir, to.pathAndName())
            process("modified attr", "$sourceFile -> $targetFile", to.isEmptyFile) {
                checkIsUnchanged(sourceFile, to)
                checkIsUnchanged(targetFile, from)
                fs.copyLastModified(sourceFile, targetFile)
                syncResultFilesSource.replace(to)
                currentFilesTarget.replace(to)
            }
        }
    }

    return actions
}