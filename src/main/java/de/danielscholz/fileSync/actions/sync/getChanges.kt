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

    val added = equalsBy(pathAndName) {
        (currentFiles - lastSyncResultFiles).toMutableSet()
    }

    val deleted = equalsBy(pathAndName) {
        (lastSyncResultFiles - currentFiles).toMutableSet()
    }

    val movedOrRenamed = mutableSetOf<MovedOrRenamed>()
    val movedAndContentChanged = mutableSetOf<MovedAndContentChanged>()

    equalsBy(PATH + HASH + MODIFIED, true) {
        (deleted intersect added)
            .map { MovedOrRenamed(it.left, it.right) }
            .ifNotEmpty {
                deleted -= it.from().toSet()
                added -= it.to().toSet()
                movedOrRenamed += it
            }
    }

    equalsBy(FILENAME + HASH + MODIFIED, true) {
        (deleted intersect added)
            .map { MovedOrRenamed(it.left, it.right) }
            .ifNotEmpty {
                deleted -= it.from().toSet()
                added -= it.to().toSet()
                movedOrRenamed += it
            }
    }

    equalsBy(HASH + MODIFIED, true) {
        (deleted intersect added)
            .map { MovedOrRenamed(it.left, it.right) }
            .ifNotEmpty {
                deleted -= it.from().toSet()
                added -= it.to().toSet()
                movedOrRenamed += it
            }
    }

    // special case: folder renamed and content changed
    equalsBy(object : EqualsAndHashCodeSupplier<FileEntity> {
        override fun equals(obj1: FileEntity, obj2: FileEntity) =
            obj1.name == obj2.name &&
                    (currentFilesResult.folderPathRenamed[obj1.folderId] ?: obj1.folderId) == (currentFilesResult.folderPathRenamed[obj2.folderId] ?: obj2.folderId)

        override fun hashCode(obj: FileEntity) = obj.name.hashCode()

    }) {
        (deleted.filter { !it.isFolderMarker } intersect added.filter { !it.isFolderMarker })
            .map { MovedAndContentChanged(it.left, it.right) }
            .ifNotEmpty {
                deleted -= it.from().toSet()
                added -= it.to().toSet()
                movedAndContentChanged += it
            }
    }

    val contentChanged = equalsBy(pathAndName) {
        (lastSyncResultFiles intersect currentFiles)
            .filter(HASH_NEQ)
            .map { ContentChanged(it.left, it.right) }
            .toMutableSet()
    }

    val modifiedChanged = equalsBy(pathAndName) {
        (lastSyncResultFiles intersect currentFiles)
            .filter(HASH_EQ and MODIFIED_NEQ)
            .map { ModifiedChanged(it.left, it.right) }
            .toMutableSet()
    }

    println("$dir successfully read")

    return MutableChanges(
        added = added,
        deleted = deleted,
        contentChanged = contentChanged,
        movedOrRenamed = movedOrRenamed,
        movedAndContentChanged = movedAndContentChanged,
        modifiedChanged = modifiedChanged,
    )
}