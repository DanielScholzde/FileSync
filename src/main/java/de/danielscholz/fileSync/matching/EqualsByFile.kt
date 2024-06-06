package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.persistence.File2
import java.util.*


fun <R> equalsBy(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<File2>.() -> R): R {

    val equalsBy = EqualsBy(ignoreDuplicatesOnIntersect, EqualsAndHashCodeSupplierImpl(mode))

    return equalsBy.block()
}
