package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


fun <R> equalsBy(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R =
    equalsBy(EqualsAndHashCodeSupplierImpl(mode), ignoreDuplicatesOnIntersect, block)


fun <R> equalsBy(equalsAndHashcodeSupplier: EqualsAndHashCodeSupplier<FileEntity>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<FileEntity>.() -> R): R {

    val equalsBy = EqualsBy(ignoreDuplicatesOnIntersect, equalsAndHashcodeSupplier)

    return equalsBy.block()
}