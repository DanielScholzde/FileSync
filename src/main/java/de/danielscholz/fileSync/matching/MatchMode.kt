package de.danielscholz.fileSync.matching

import java.util.*

enum class MatchMode {
    PATH,
    FILENAME,
    HASH,
    MODIFIED,
}

val pathAndName: EnumSet<MatchMode> = EnumSet.of(MatchMode.PATH, MatchMode.FILENAME)
