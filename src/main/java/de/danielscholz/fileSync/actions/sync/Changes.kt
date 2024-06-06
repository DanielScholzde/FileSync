package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.persistence.File2
import kotlinx.datetime.Instant


class Changes(
    val added: MutableSet<File2>,
    val deleted: MutableSet<File2>,
    val contentChanged: MutableSet<ContentChanged>,
    val modifiedChanged: Set<ModifiedChanged>,
    val movedOrRenamed: MutableSet<MovedOrRenamed>,
    val allFilesBeforeSync: Set<File2>,
) {
    init {
        // all sets/collections must be disjoint
        val allAsSet: Set<File2> = added + deleted + contentChanged.to() + modifiedChanged.to() + movedOrRenamed.from() + movedOrRenamed.to()
        if (added.size + deleted.size + contentChanged.size + modifiedChanged.size + 2 * movedOrRenamed.size != allAsSet.size) {
            throw IllegalStateException()
        }
    }

    fun hasChanges() = added.isNotEmpty() || deleted.isNotEmpty() || contentChanged.isNotEmpty() || modifiedChanged.isNotEmpty() || movedOrRenamed.isNotEmpty()
}


interface IChange<FROM : File2?, TO : File2?> {
    val from: FROM
    val to: TO
}

/** equals/hashCode: only 'to' is considered! */
data class ContentChanged(override val from: File2, override val to: File2) : IChange<File2, File2> {
    companion object {
        val DOES_NOT_MATTER_FILE = File2(0, "-", Instant.DISTANT_PAST, Instant.DISTANT_PAST, true, 0, null)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContentChanged) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}

/** equals/hashCode: only 'to' is considered! */
data class ModifiedChanged(override val from: File2, override val to: File2) : IChange<File2, File2> {
    companion object {
        val DOES_NOT_MATTER_FILE = File2(0, "-", Instant.DISTANT_PAST, Instant.DISTANT_PAST, true, 0, null)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModifiedChanged) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}

/** equals/hashCode: only 'to' is considered! */
data class MovedOrRenamed(override val from: File2, override val to: File2) : IChange<File2, File2> {
    companion object {
        val DOES_NOT_MATTER_FILE = File2(0, "-", Instant.DISTANT_PAST, Instant.DISTANT_PAST, true, 0, null)
    }

    val renamed get() = from.name != to.name
    val moved get() = from.folderId != to.folderId

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MovedOrRenamed) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}

/** equals/hashCode: only 'from' is considered! */
data class Deletion(override val from: File2) : IChange<File2, Nothing?> {
    override val to = null

    val file = from

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Deletion) return false
        return from == other.from
    }

    override fun hashCode() = from.hashCode()
}

/** equals/hashCode: only 'to' is considered! */
data class Addition(override val to: File2) : IChange<Nothing?, File2> {
    override val from = null

    val file = to

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Addition) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}
