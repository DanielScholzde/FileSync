package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.actions.sync.IChange
import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


context(CaseSensitiveContext, FoldersContext)
fun <T : IChange<FileEntity, FileEntity>, R> equalsForFileChangeToBy(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<T>.() -> R): R =
    equalsBy(object : EqualsAndHashCodeSupplier<T> {

        val delegate = EqualsAndHashCodeSupplierForFile(mode, foldersCtx, isCaseSensitive)

        override fun equals(obj1: T, obj2: T) = delegate.equals(obj1.to, obj2.to)

        override fun hashCode(obj: T): Int = delegate.hashCode(obj.to)

    }, ignoreDuplicatesOnIntersect, block)
