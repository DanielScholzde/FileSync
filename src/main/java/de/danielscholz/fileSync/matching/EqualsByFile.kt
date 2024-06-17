package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


context(CaseSensitiveContext, FoldersContext)
fun <R> equalsBy(mode: MatchMode, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R =
    equalsBy(EnumSet.of(mode), ignoreDuplicatesOnIntersect, block)


context(CaseSensitiveContext, FoldersContext)
fun <R> equalsBy(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R =
    equalsBy(EqualsAndHashCodeSupplierImpl(mode, foldersCtx, isCaseSensitive), ignoreDuplicatesOnIntersect, block)



fun <R> equalsBy(equalsAndHashcodeSupplier: EqualsAndHashCodeSupplier<FileEntity>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R {

    val equalsBy = EqualsBy(ignoreDuplicatesOnIntersect, equalsAndHashcodeSupplier)

    return equalsBy.block()
}