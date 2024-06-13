package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.MutableFoldersContext
import de.danielscholz.fileSync.common.ifNotEmpty
import de.danielscholz.fileSync.common.isCaseSensitiveFileSystem
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.matching.MatchMode.*
import de.danielscholz.fileSync.persistence.File2
import de.danielscholz.fileSync.persistence.isFolderMarker
import java.io.File


context(MutableFoldersContext)
fun getChanges(dir: File, lastSyncResultFiles: List<File2>, filter: Filter, statistics: Statistics): MutableChanges {
    val caseSensitiveContext = CaseSensitiveContext(
        isCaseSensitiveFileSystem(dir) ?: throw Exception("Unable to determine if filesystem $dir is case sensitive!")
    )
    return with(caseSensitiveContext) {

        val current = getCurrentFiles(dir, filter, lastSyncResultFiles, statistics)
        val currentFiles = current.files

        @Suppress("ConvertArgumentToSet")
        val added = equalsBy(pathAndName) {
            (currentFiles - lastSyncResultFiles).toMutableSet()
        }

        @Suppress("ConvertArgumentToSet")
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
        equalsBy(object : EqualsAndHashCodeSupplier<File2> {
            override fun equals(obj1: File2, obj2: File2) =
                obj1.name == obj2.name &&
                        (current.folderPathRenamed[obj1.folderId] ?: obj1.folderId) == (current.folderPathRenamed[obj2.folderId] ?: obj2.folderId)

            override fun hashCode(obj: File2) = obj.name.hashCode()

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

        MutableChanges(
            added = added,
            deleted = deleted,
            contentChanged = contentChanged,
            movedOrRenamed = movedOrRenamed,
            movedAndContentChanged = movedAndContentChanged,
            modifiedChanged = modifiedChanged,
            allFilesBeforeSync = currentFiles.toSet(),
        )
    }
}