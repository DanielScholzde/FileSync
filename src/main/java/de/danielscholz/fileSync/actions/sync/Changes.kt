package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.persistence.File2
import kotlinx.datetime.Instant


class Changes(
    val added: MutableSet<File2>,
    val deleted: MutableSet<File2>,
    val contentChanged: MutableSet<Change>,
    val attributesChanged: Set<Change>,
    val movedOrRenamed: MutableSet<Change>,
    val allFilesBeforeSync: Set<File2>,
) {
    init {
        // all sets/collections must be disjoint
        if (added.size + deleted.size + contentChanged.size + attributesChanged.size + 2 * movedOrRenamed.size !=
            (added + deleted + contentChanged + attributesChanged + movedOrRenamed.from() + movedOrRenamed.to()).size
        ) {
            throw IllegalStateException()
        }
    }

    fun hasChanges() = added.isNotEmpty() || deleted.isNotEmpty() || contentChanged.isNotEmpty() || attributesChanged.isNotEmpty() || movedOrRenamed.isNotEmpty()
}


/** equals/hashCode: only 'to' is considered! */
data class Change(val from: File2, val to: File2) {
    companion object {
        val DOES_NOT_MATTER_FILE = File2(0, "-", Instant.DISTANT_PAST, Instant.DISTANT_PAST, true, 0, null)
    }

    val renamed get() = from.name != to.name
    val moved get() = from.folderId != to.folderId

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Change) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}
