package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.MutableFoldersContext
import de.danielscholz.fileSync.common.ifNotEmpty
import de.danielscholz.fileSync.common.isCaseSensitiveFileSystem
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.matching.MatchMode.*
import de.danielscholz.fileSync.persistence.File2
import java.io.File


context(MutableFoldersContext)
fun getChanges(dir: File, lastSyncResultFiles: List<File2>, filter: Filter, statistics: Statistics): MutableChanges {
    val caseSensitiveContext = CaseSensitiveContext(
        isCaseSensitiveFileSystem(dir) ?: throw Exception("Unable to determine if filesystem $dir is case sensitive!")
    )
    return with(caseSensitiveContext) {

        val current = getCurrentFiles(dir, filter, lastSyncResultFiles, statistics)

        @Suppress("ConvertArgumentToSet")
        val added = equalsBy(pathAndName) {
            (current - lastSyncResultFiles).toMutableSet()
        }

        @Suppress("ConvertArgumentToSet")
        val deleted = equalsBy(pathAndName) {
            (lastSyncResultFiles - current).toMutableSet()
        }

        val movedOrRenamed = mutableSetOf<MovedOrRenamed>()

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

        val contentChanged = equalsBy(pathAndName) {
            (lastSyncResultFiles intersect current)
                .filter(HASH_NEQ)
                .map { ContentChanged(it.left, it.right) }
                .toMutableSet()
        }

        val modifiedChanged = equalsBy(pathAndName) {
            (lastSyncResultFiles intersect current)
                .filter(HASH_EQ and MODIFIED_NEQ)
                .map { ModifiedChanged(it.left, it.right) }
                .toMutableSet()
        }

        MutableChanges(
            added = added,
            deleted = deleted,
            contentChanged = contentChanged,
            modifiedChanged = modifiedChanged,
            movedOrRenamed = movedOrRenamed,
            allFilesBeforeSync = current.toSet()
        )
    }
}