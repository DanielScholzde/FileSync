package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.actions.Folders
import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


fun <R> equalsForFileBy(mode: MatchMode, folders: Folders, caseSensitive: Boolean, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R =
    equalsForFileBy(EnumSet.of(mode), folders, caseSensitive, ignoreDuplicatesOnIntersect, block)


fun <R> equalsForFileBy(
    mode: EnumSet<MatchMode>,
    folders: Folders,
    caseSensitive: Boolean,
    ignoreDuplicatesOnIntersect: Boolean = false,
    block: EqualsBy<FileEntity>.() -> R
): R =
    equalsBy(EqualsAndHashCodeSupplierForFile(mode, folders, caseSensitive), ignoreDuplicatesOnIntersect, block)
