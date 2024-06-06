package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.matching.from
import de.danielscholz.fileSync.matching.to
import de.danielscholz.fileSync.persistence.File2
import kotlinx.datetime.Instant


class Changes(
    val added: MutableSet<File2>,
    val deleted: MutableSet<File2>,
    val contentChanged: MutableSet<ContentChanged>,
    val attributesChanged: Set<File2>,
    val movedOrRenamed: List<Moved>,
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

abstract class FromTo {
    abstract val from: File2
    abstract val to: File2

    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FromTo) return false // TODO
        return to == other.to
    }

    final override fun hashCode() = to.hashCode()
}

/** equals/hashCode: only 'to' is considered! */
data class Moved(override val from: File2, override val to: File2) : FromTo() {
    val renamed get() = from.name != to.name
    val moved get() = from.folderId != to.folderId
}

/** equals/hashCode: only 'to' is considered! */
data class ContentChanged(override val from: File2, override val to: File2) : FromTo() {
    companion object {
        val DOES_NOT_MATTER_FILE = File2(0, "-", Instant.DISTANT_PAST, Instant.DISTANT_PAST, true, 0, null)
    }
}