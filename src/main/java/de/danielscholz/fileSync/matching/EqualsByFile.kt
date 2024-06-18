package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.actions.sync.IChange
import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


context(CaseSensitiveContext, FoldersContext)
fun <R> equalsBy(mode: MatchMode, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R =
    equalsBy(EnumSet.of(mode), ignoreDuplicatesOnIntersect, block)


context(CaseSensitiveContext, FoldersContext)
fun <R> equalsBy(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R =
    equalsBy(EqualsAndHashCodeSupplierForFileEntity(mode, foldersCtx, isCaseSensitive), ignoreDuplicatesOnIntersect, block)


context(CaseSensitiveContext, FoldersContext)
fun <T : IChange<FileEntity, FileEntity>, R> equalsByTo(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<T>.() -> R): R =
    equalsBy(object : EqualsAndHashCodeSupplier<T> {

        val delegate = EqualsAndHashCodeSupplierForFileEntity(mode, foldersCtx, isCaseSensitive)

        override fun equals(obj1: T, obj2: T) = delegate.equals(obj1.to, obj2.to)

        override fun hashCode(obj: T): Int = delegate.hashCode(obj.to)

    }, ignoreDuplicatesOnIntersect, block)


fun <T : Any, R> equalsBy(equalsAndHashcodeSupplier: EqualsAndHashCodeSupplier<T>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<T>.() -> R): R {

    val equalsBy = EqualsBy(ignoreDuplicatesOnIntersect, equalsAndHashcodeSupplier)

    return equalsBy.block()
}