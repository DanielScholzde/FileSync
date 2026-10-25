package de.danielscholz.fileSync.matching

import java.util.*


operator fun MatchMode.plus(matchMode: MatchMode): EnumSet<MatchMode> {
    return EnumSet.of(this, matchMode)
}

operator fun EnumSet<MatchMode>.plus(matchMode: MatchMode): EnumSet<MatchMode> {
    val copy: EnumSet<MatchMode> = EnumSet.copyOf(this)
    copy.add(matchMode)
    return copy
}
