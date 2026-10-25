package de.danielscholz.fileSync.matching


enum class MatchMode {
    PATH,
    FILENAME,
    HASH,
    MODIFIED,
}


val pathAndName = MatchMode.PATH + MatchMode.FILENAME
