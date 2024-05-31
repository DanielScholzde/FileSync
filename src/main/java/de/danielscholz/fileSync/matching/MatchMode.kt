package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import java.util.*

enum class MatchMode {
    PATH,
    FILENAME,
    HASH,
    MODIFIED,
}

val matchModePathAndFilename = MatchMode.PATH + MatchMode.FILENAME

context(FoldersContext, CaseSensitiveContext)
val pathAndName: KeySupplier
    get() = matchMode(matchModePathAndFilename)


context(FoldersContext, CaseSensitiveContext)
fun matchMode(matchMode: EnumSet<MatchMode>) =
    KeySupplier { createKey(it, matchMode) }