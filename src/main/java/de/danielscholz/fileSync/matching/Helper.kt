package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.actions.SyncFiles
import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.persistence.File2
import java.util.*


context(FoldersContext, CaseSensitiveContext)
fun createKey(file: File2, mode: EnumSet<MatchMode>): String {
    val b = StringBuilder(100)
    if (MatchMode.HASH in mode) {
        b.append("h=").append(file.hash?.hash).append("@")
    }

    // Workaround for empty files (which do not have a hash)
    if (file.hash == null && MatchMode.HASH in mode && MatchMode.FILENAME !in mode) {
        b.append("fn=").append(file.name).append("@")
    }
    if (file.hash == null && MatchMode.HASH in mode && MatchMode.PATH !in mode) {
        val path = foldersCtx.folders[file.folderId]!!.fullPath
        b.append("p=").append(if (isCaseSensitive) path else path.lowercase()).append("@")
    }

    if (MatchMode.PATH in mode) {
        val path = foldersCtx.folders[file.folderId]!!.fullPath
        b.append("p=").append(if (isCaseSensitive) path else path.lowercase()).append("@")
    }
    if (MatchMode.FILENAME in mode) {
        b.append("fn=").append(file.name).append("@")
    }
    if (MatchMode.MODIFIED in mode) {
        b.append("m=").append(file.modified).append("@")
    }
    return b.toString()
}

operator fun MatchMode.plus(matchMode: MatchMode): EnumSet<MatchMode> {
    return EnumSet.of(this, matchMode)
}

operator fun EnumSet<MatchMode>.plus(matchMode: MatchMode): EnumSet<MatchMode> {
    val copy: EnumSet<MatchMode> = EnumSet.copyOf(this)
    copy.add(matchMode)
    return copy
}

fun Collection<Pair<File2, File2>>.filter2(resultFilter: ResultFilter): Collection<Pair<File2, File2>> {
    return this.filter { resultFilter.filter(it.first, it.second) }
}

fun Collection<Pair<File2, File2>>.left() = this.map { it.first }
fun Collection<Pair<File2, File2>>.right() = this.map { it.second }

fun Collection<SyncFiles.FromTo>.from() = this.map { it.from }
fun Collection<SyncFiles.FromTo>.to() = this.map { it.to }
