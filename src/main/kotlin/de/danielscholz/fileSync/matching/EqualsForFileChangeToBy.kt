package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.actions.Folders
import de.danielscholz.fileSync.actions.sync.IChange
import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


fun <T : IChange<FileEntity, FileEntity>, R> equalsForFileChangeToBy(
    mode: EnumSet<MatchMode>,
    folders: Folders,
    caseSensitive: Boolean,
    ignoreDuplicatesOnIntersect: Boolean = false,
    block: EqualsBy<T>.() -> R
): R =
    equalsBy(object : EqualsAndHashCodeSupplier<T> {

        val delegate = EqualsAndHashCodeSupplierForFile(mode, folders, caseSensitive)

        override fun equals(obj1: T, obj2: T) = delegate.equals(obj1.to, obj2.to)

        override fun hashCode(obj: T): Int = delegate.hashCode(obj.to)

    }, ignoreDuplicatesOnIntersect, block)
