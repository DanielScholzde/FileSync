package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.actions.sync.FromTo
import de.danielscholz.fileSync.persistence.File2
import java.util.*


operator fun MatchMode.plus(matchMode: MatchMode): EnumSet<MatchMode> {
    return EnumSet.of(this, matchMode)
}

operator fun EnumSet<MatchMode>.plus(matchMode: MatchMode): EnumSet<MatchMode> {
    val copy: EnumSet<MatchMode> = EnumSet.copyOf(this)
    copy.add(matchMode)
    return copy
}


fun Collection<Pair<File2, File2>>.leftSide() = this.map { it.first }
fun Collection<Pair<File2, File2>>.rightSide() = this.map { it.second }

fun Collection<FromTo>.from() = this.map { it.from }
fun Collection<FromTo>.to() = this.map { it.to }
