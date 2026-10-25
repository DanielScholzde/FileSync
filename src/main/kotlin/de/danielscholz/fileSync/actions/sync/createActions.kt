package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.actions.Folders
import de.danielscholz.fileSync.common.addWithCheck
import de.danielscholz.fileSync.common.plus
import de.danielscholz.fileSync.common.removeWithCheck
import de.danielscholz.fileSync.common.replace
import de.danielscholz.fileSync.persistence.isEmptyFile
import de.danielscholz.fileSync.persistence.isFolderMarker
import java.io.File


fun createActions(sourceChanges: Changes, targetChanges: Changes, folders: Folders): List<Action> =
    sourceChanges.createActions(switchedSourceAndTarget = false, locationOfChangesToBeMade = Location.TARGET, folders) +
            targetChanges.createActions(switchedSourceAndTarget = true, locationOfChangesToBeMade = Location.SOURCE, folders)


private fun Changes.createActions(switchedSourceAndTarget: Boolean, locationOfChangesToBeMade: Location, folders: Folders): List<Action> {

    val actions = mutableListOf<Action>()

//    folderRenamed.forEach {(oldFolderId, currentFolderId) ->
//        actions += Action(currentFolderId, "", locationOfChangesToBeMade, switchedSourceAndTarget, -2) {
//            val from = File(targetDir, folders.getFullPath(oldFolderId))
//            val to = File(targetDir, folders.getFullPath(currentFolderId))
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
                val directoryToCreate = File(targetDir, it.path(folders))
                processDir("create dir", "$directoryToCreate") {
                    fs.createDirsFor(directoryToCreate)
                    syncResultFiles.addWithCheck(it)
                    currentFilesTarget.addWithCheck(it)
                }
            }
        } else {
            Action(it.folderId, it.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
                val sourceFile = File(sourceDir, it.pathAndName(folders))
                val targetFile = File(targetDir, it.pathAndName(folders))
                process("add", "$sourceFile -> $targetFile", it.isEmptyFile) {
                    checkIsUnchanged(sourceFile, it)
                    fs.copy(sourceFile, targetFile, it.fileHash?.hash).processResult()
                    syncResultFiles.addWithCheck(it)
                    currentFilesTarget.addWithCheck(it)
                    bytesCopied(it.size)
                }
            }
        }
    }

    contentChanged.forEach { (from, to) ->
        // pathAndName() must be equals in 'from' and 'to'
        actions += Action(to.folderId, to.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
            val sourceFile = File(sourceDir, to.pathAndName(folders))
            val targetFile = File(targetDir, to.pathAndName(folders))
            process("copy", "$sourceFile -> $targetFile", to.isEmptyFile) {
                checkIsUnchanged(sourceFile, to)
                checkIsUnchanged(targetFile, from)
                val backupFile = File(File(targetDir, changedDir), to.pathAndName(folders))
                fs.createDirsFor(backupFile.parentFile)
                fs.move(targetFile, backupFile, from.fileHash?.hash)
                fs.copy(sourceFile, targetFile, to.fileHash?.hash).processResult()
                syncResultFiles.replace(to)
                currentFilesTarget.replace(to)
                bytesCopied(to.size)
            }
        }
    }

    movedOrRenamed.forEach {
        val (from, to) = it
        actions += Action(to.folderId, to.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
            val sourceFile = File(targetDir, from.pathAndName(folders))
            val targetFile = File(targetDir, to.pathAndName(folders))
            val action = if (it.moved && it.renamed) "move+rename" else if (it.moved) "move" else "rename"
            process(action, "$sourceFile -> $targetFile", to.isEmptyFile) {
                checkIsUnchanged(sourceFile, to)
                // special case: change in lower/upper case only
                if (it.renamed && !it.moved && from.name != to.name && from.nameLowercase == to.nameLowercase) {
                    val tmpFile = targetFile.resolveSibling(targetFile.name + "__tmp")
                    if (!tmpFile.exists()) {
                        fs.move(sourceFile, tmpFile, from.fileHash?.hash)
                        fs.move(tmpFile, targetFile, from.fileHash?.hash).processResult()
                    } else throw Exception("tmp file already exists!")
                } else {
                    fs.move(sourceFile, targetFile, from.fileHash?.hash).processResult()
                }
                syncResultFiles.removeWithCheck(from)
                syncResultFiles.addWithCheck(to)
                currentFilesTarget.removeWithCheck(from)
                currentFilesTarget.addWithCheck(to)
            }
        }
    }

    movedAndContentChanged.forEach {
        val (from, to) = it
        actions += Action(to.folderId, to.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
            val sourceFile = File(sourceDir, to.pathAndName(folders))
            val targetFromFile = File(targetDir, from.pathAndName(folders))
            val targetToFile = File(targetDir, to.pathAndName(folders))
            process("copy", "$sourceFile -> $targetToFile", to.isEmptyFile) {
                checkIsUnchanged(sourceFile, to)
                val backupFile = File(File(targetDir, changedDir), from.pathAndName(folders))
                fs.createDirsFor(backupFile.parentFile)
                fs.move(targetFromFile, backupFile, from.fileHash?.hash)
                fs.copy(sourceFile, targetToFile, to.fileHash?.hash).processResult()
                syncResultFiles.removeWithCheck(from)
                syncResultFiles.addWithCheck(to)
                currentFilesTarget.removeWithCheck(from)
                currentFilesTarget.addWithCheck(to)
                bytesCopied(to.size)
            }
        }
    }

    deleted.forEach {
        actions += if (it.isFolderMarker) {
            Action(it.folderId, "", locationOfChangesToBeMade, switchedSourceAndTarget, 100 - folders.getDepth(it.folderId)) {
                val directoryToDelete = File(targetDir, it.path(folders))
                processDir("delete dir", "$directoryToDelete") {
                    val file = File(directoryToDelete, "Thumbs.db")
                    if (file.isFile) fs.deleteFile(file)
                    fs.deleteDir(directoryToDelete)
                    syncResultFiles.removeWithCheck(it)
                    currentFilesTarget.removeWithCheck(it)
                }
            }
        } else {
            Action(it.folderId, it.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
                val toDelete = File(targetDir, it.pathAndName(folders))
                val backupFile = File(File(targetDir, deletedDir), it.pathAndName(folders))
                process("delete", "$toDelete", it.isEmptyFile) {
                    checkIsUnchanged(toDelete, it)
                    fs.createDirsFor(backupFile.parentFile)
                    fs.move(toDelete, backupFile, it.fileHash?.hash)
                    syncResultFiles.removeWithCheck(it)
                    currentFilesTarget.removeWithCheck(it)
                }
            }
        }
    }

    modifiedChanged.forEach { (from, to) ->
        actions += Action(to.folderId, to.name, locationOfChangesToBeMade, switchedSourceAndTarget) {
            val sourceFile = File(sourceDir, to.pathAndName(folders))
            val targetFile = File(targetDir, to.pathAndName(folders))
            process("modified attr", "$sourceFile -> $targetFile", to.isEmptyFile) {
                checkIsUnchanged(sourceFile, to)
                checkIsUnchanged(targetFile, from)
                fs.copyLastModified(sourceFile, targetFile)
                syncResultFiles.replace(to)
                currentFilesTarget.replace(to)
            }
        }
    }

    return actions
}