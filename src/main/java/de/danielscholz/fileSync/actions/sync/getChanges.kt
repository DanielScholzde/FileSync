package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.common.ifNotEmpty
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.matching.MatchMode.*
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.persistence.isFolderMarker
import java.io.File


context(CaseSensitiveContext, FoldersContext)
fun getChanges(dir: File, lastSyncResultFiles: Set<FileEntity>, currentFilesResult: CurrentFiles): MutableChanges {

    val currentFiles = currentFilesResult.files

    val added = equalsForFileBy(pathAndName) {
        (currentFiles - lastSyncResultFiles).toMutableSet()
    }

    val deleted = equalsForFileBy(pathAndName) {
        (lastSyncResultFiles - currentFiles).toMutableSet()
    }

    val movedOrRenamed = mutableSetOf<MovedOrRenamed>()
    val movedAndContentChanged = mutableSetOf<MovedAndContentChanged>()
    //val folderRenamed = mutableListOf<Pair<Long, Long>>()

//    equalsBy(FILENAME + HASH + MODIFIED, true) {
//
//        data class FolderRenamed(val oldFolderId: Long, val currentFolderId: Long, val fileCount: Int)
//
//        val intersectResults = deleted intersect added
//
//        val folderRenamedCandidates = intersectResults
//            .map { it.left.folderId to it.right.folderId }
//            .groupBy { it.second } // group by current/new folderId
//            .map { it.key to it.value.map { it.first } }
//            .map { (currentFolderId, oldFolderIds) ->
//                val (oldFolderId, count) = oldFolderIds
//                    .groupingBy { it }
//                    .eachCount()
//                    .maxBy { it.value }
//
//                FolderRenamed(oldFolderId, currentFolderId, count)
//            }
//
//        if (folderRenamedCandidates.isNotEmpty()) {
//            val lastSyncFolderIds = lastSyncResultFiles.filter { it.isFolderMarker }.map { it.folderId }.toSet()
//            val currentFolderIds = currentFiles.filter { it.isFolderMarker }.map { it.folderId }.toSet()
//
//            folderRenamedCandidates
//                // old folder must not exist any longer AND 'new folder' must not exist within folders of last sync
//                .filter { it.oldFolderId !in currentFolderIds && it.currentFolderId !in lastSyncFolderIds }
//                .filter { (_, newFolderId, countMoved) ->
//                    // count current files within current/new folder (-1 because of virtual folder marker file)
//                    val totalFilesWithinCurrentFolder = currentFiles.count { it.folderId == newFolderId } - 1
//                    countMoved * 100 / totalFilesWithinCurrentFolder >= 66
//                }
//                .filter {
//                    // only consider the 'root' change
//                    foldersCtx.get(it.oldFolderId).parentFolderId == foldersCtx.get(it.currentFolderId).parentFolderId
//                }
//                .forEach { (oldFolderId, currentFolderId) ->
//                    println("Folder renamed : ${foldersCtx.getFullPath(oldFolderId)} --> ${foldersCtx.getFullPath(currentFolderId)}")
//                    folderRenamed.add(oldFolderId to currentFolderId)
//
//                    fun make(oldFolderId: Long, currentFolderId: Long) {
//                        intersectResults.forEach { (deletedFile, addedFile) ->
//                            if (deletedFile.folderId == oldFolderId && addedFile.folderId == currentFolderId) {
//                                deleted.removeWithCheck(deletedFile)
//                                added.removeWithCheck(addedFile)
//                            }
//                        }
//                        // fix folderMarker, because they are not always included within intersectResults:
//                        deleted.removeIf { it.isFolderMarker && it.folderId == oldFolderId }
//                        added.removeIf { it.isFolderMarker && it.folderId == currentFolderId }
//
//                        val map1 = foldersCtx.get(oldFolderId).children.associateBy { it.name }
//                        val map2 = foldersCtx.get(currentFolderId).children.associateBy { it.name }
//                        (map1.keys intersect map2.keys).forEach { folderName ->
//                            val from = map1[folderName]!!.id
//                            val to = map2[folderName]!!.id
//                            make(from, to)
//                        }
//                    }
//
//                    make(oldFolderId, currentFolderId)
//                }
//        }
//    }


    // file renamed
    equalsForFileBy(PATH + HASH + MODIFIED, true) {
        (deleted intersect added)
            .filter { !it.left.isFolderMarker and !it.right.isFolderMarker }
            .map { MovedOrRenamed(it.left, it.right) }
            .ifNotEmpty {
                deleted -= it.from().toSet()
                added -= it.to().toSet()
                movedOrRenamed += it
            }
    }

    // file moved to other folder
    equalsForFileBy(FILENAME + HASH + MODIFIED, true) {
        (deleted intersect added)
            .filter { !it.left.isFolderMarker and !it.right.isFolderMarker }
            .map { MovedOrRenamed(it.left, it.right) }
            .ifNotEmpty {
                deleted -= it.from().toSet()
                added -= it.to().toSet()
                movedOrRenamed += it
            }
    }

    // file renamed and moved to other folder
    equalsForFileBy(HASH + MODIFIED, true) {
        (deleted intersect added)
            .filter { !it.left.isFolderMarker and !it.right.isFolderMarker }
            .map { MovedOrRenamed(it.left, it.right) }
            .ifNotEmpty {
                deleted -= it.from().toSet()
                added -= it.to().toSet()
                movedOrRenamed += it
            }
    }

    // special case: file moved to other folder and file content changed (but filename still the same)
    equalsForFileBy(FILENAME, true) {
        (deleted intersect added)
            .filter { !it.left.isFolderMarker and !it.right.isFolderMarker }
            .filter(HASH_NEQ)
            .map { MovedAndContentChanged(it.left, it.right) }
            .ifNotEmpty {
                deleted -= it.from().toSet()
                added -= it.to().toSet()
                movedAndContentChanged += it
            }
    }

    val contentChanged = equalsForFileBy(pathAndName) {
        (lastSyncResultFiles intersect currentFiles)
            .filter(HASH_NEQ)
            .map { ContentChanged(it.left, it.right) }
            .toMutableSet()
    }

    // attribute 'modification date' changed, but content is still the same
    val modifiedChanged = equalsForFileBy(pathAndName) {
        (lastSyncResultFiles intersect currentFiles)
            .filter(HASH_EQ and MODIFIED_NEQ)
            .map { ModifiedChanged(it.left, it.right) }
            .toMutableSet()
    }

    println("$dir successfully read")

    return MutableChanges(
        //folderRenamed = folderRenamed,
        added = added,
        deleted = deleted,
        contentChanged = contentChanged,
        movedOrRenamed = movedOrRenamed,
        movedAndContentChanged = movedAndContentChanged,
        modifiedChanged = modifiedChanged,
    )
}