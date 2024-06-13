package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.persistence.FileEntity
import kotlinx.datetime.Instant


interface Changes {
    val added: Set<FileEntity>
    val deleted: Set<FileEntity>
    val contentChanged: Set<ContentChanged>
    val movedAndContentChanged: Set<MovedAndContentChanged>
    val movedOrRenamed: Set<MovedOrRenamed>
    val modifiedChanged: Set<ModifiedChanged>
    val allFilesBeforeSync: Set<FileEntity>
    fun hasChanges(): Boolean
}

class MutableChanges(
    override val added: MutableSet<FileEntity>,
    override val deleted: MutableSet<FileEntity>,
    override val contentChanged: MutableSet<ContentChanged>,
    override val movedAndContentChanged: MutableSet<MovedAndContentChanged>,
    override val movedOrRenamed: MutableSet<MovedOrRenamed>,
    override val modifiedChanged: Set<ModifiedChanged>,
    override val allFilesBeforeSync: Set<FileEntity>,
) : Changes {
    init {
        // all sets/collections must be disjoint
        val allAsSet: Set<FileEntity> = added +
                deleted +
                contentChanged.to() +
                movedAndContentChanged.from() +
                movedAndContentChanged.to() +
                movedOrRenamed.from() +
                movedOrRenamed.to() +
                modifiedChanged.to()
        val added = added.size +
                deleted.size +
                contentChanged.size +
                2 * movedAndContentChanged.size +
                2 * movedOrRenamed.size +
                modifiedChanged.size
        if (added != allAsSet.size) {
            throw IllegalStateException()
        }
    }

    override fun hasChanges() = added.isNotEmpty() ||
            deleted.isNotEmpty() ||
            contentChanged.isNotEmpty() ||
            movedAndContentChanged.isNotEmpty() ||
            movedOrRenamed.isNotEmpty() ||
            modifiedChanged.isNotEmpty()
}


interface IChange<FROM : FileEntity?, TO : FileEntity?> {
    val from: FROM
    val to: TO
}

/** equals/hashCode: only 'to' is considered! */
data class ContentChanged(override val from: FileEntity, override val to: FileEntity) : IChange<FileEntity, FileEntity> {
    companion object {
        val DOES_NOT_MATTER_FILE = FileEntity(0, "-", Instant.DISTANT_PAST, Instant.DISTANT_PAST, true, 0, null)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContentChanged) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}

/** equals/hashCode: only 'to' is considered! */
data class ModifiedChanged(override val from: FileEntity, override val to: FileEntity) : IChange<FileEntity, FileEntity> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModifiedChanged) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}

/** equals/hashCode: only 'to' is considered! */
data class MovedOrRenamed(override val from: FileEntity, override val to: FileEntity) : IChange<FileEntity, FileEntity> {

    val renamed get() = from.name != to.name
    val moved get() = from.folderId != to.folderId

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MovedOrRenamed) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}

/** equals/hashCode: only 'to' is considered! */
data class MovedAndContentChanged(override val from: FileEntity, override val to: FileEntity) : IChange<FileEntity, FileEntity> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MovedAndContentChanged) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}

/** equals/hashCode: only 'from' is considered! */
data class Deletion(override val from: FileEntity) : IChange<FileEntity, Nothing?> {
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
data class Addition(override val to: FileEntity) : IChange<Nothing?, FileEntity> {
    override val from = null

    val file = to

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Addition) return false
        return to == other.to
    }

    override fun hashCode() = to.hashCode()
}
