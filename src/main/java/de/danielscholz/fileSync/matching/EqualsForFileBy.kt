package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


context(CaseSensitiveContext, FoldersContext)
fun <R> equalsForFileBy(mode: MatchMode, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R =
    equalsForFileBy(EnumSet.of(mode), ignoreDuplicatesOnIntersect, block)


context(CaseSensitiveContext, FoldersContext)
fun <R> equalsForFileBy(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R =
    equalsBy(EqualsAndHashCodeSupplierForFile(mode, foldersCtx, isCaseSensitive), ignoreDuplicatesOnIntersect, block)
