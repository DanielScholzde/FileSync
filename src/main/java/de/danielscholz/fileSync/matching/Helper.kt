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
        b.append(file.hash?.hash).append("@")
    }

    // Workaround for empty files (which do not have a hash)
    if (file.hash == null && MatchMode.HASH in mode && MatchMode.FILENAME !in mode) {
        b.append(file.name).append("@")
    }
    if (file.hash == null && MatchMode.HASH in mode && MatchMode.PATH !in mode) {
        val path = foldersCtx.folders[file.folderId]!!.fullPath
        b.append(if (isCaseSensitive) path else path.lowercase()).append("@")
    }

    if (MatchMode.PATH in mode) {
        val path = foldersCtx.folders[file.folderId]!!.fullPath
        b.append(if (isCaseSensitive) path else path.lowercase()).append("@")
    }
    if (MatchMode.FILENAME in mode) {
        b.append(file.name).append("@")
    }
    if (MatchMode.MODIFIED in mode) {
        b.append(file.modified).append("@")
    }
    if (MatchMode.CREATED in mode) {
        b.append(file.created).append("@")
    }
    return b.toString()
}

//operator fun MatchMode.plus(b: MatchMode): EnumSet<MatchMode> {
//   return EnumSet.of(this, b)
//}
//
//operator fun EnumSet<MatchMode>.plus(b: MatchMode): EnumSet<MatchMode> {
//   this.add(b)
//   return this
//}


//fun Collection<File2>.subtract(
//    other: Collection<File2>,
//    mode: EnumSet<MatchMode>,
//    multimapMatching: Boolean
//): Collection<File2> {
//   return Subtract(mode, multimapMatching).apply(this, other)
//}
//
//fun Sequence<File2>.subtract(
//    other: Sequence<File2>,
//    mode: EnumSet<MatchMode>,
//    multimapMatching: Boolean
//): Sequence<File2> {
//   return SubtractSeq(mode, multimapMatching).apply(this, other)
//}
//
//fun Collection<File2>.intersect(
//    other: Collection<File2>,
//    mode: EnumSet<MatchMode>,
//    multimapMatching: Boolean
//): Collection<Pair<File2, File2>> {
//   return Intersect(mode, multimapMatching).apply(this, other)
//}
//
//fun Sequence<File2>.intersect(
//    other: Sequence<File2>,
//    mode: EnumSet<MatchMode>,
//    multimapMatching: Boolean
//): Sequence<Pair<File2, File2>> {
//   return IntersectSeq(mode, multimapMatching).apply(this, other)
//}
//
//fun Collection<File2>.union(
//    other: Collection<File2>,
//    mode: EnumSet<MatchMode>,
//    errorOnKeyCollision: Boolean
//): Collection<File2> {
//   return Union(mode, errorOnKeyCollision).apply(this, other)
//}
//
//fun Sequence<File2>.union(
//    other: Sequence<File2>,
//    mode: EnumSet<MatchMode>,
//    errorOnKeyCollision: Boolean
//): Sequence<File2> {
//   return UnionSeq(mode, errorOnKeyCollision).apply(this, other)
//}
//

//fun Collection<File2>.makeUnique(mode: EnumSet<MatchMode>, errorOnKeyCollision: Boolean = false): Collection<File2> {
//    return Unique(mode, errorOnKeyCollision).apply(this)
//}

fun Collection<File2>.filterEmptyFiles(): Collection<File2> {
    return this.filter { it.hash != null }
}

fun Collection<Pair<File2, File2>>.filter2(resultFilter: ResultFilter): Collection<Pair<File2, File2>> {
    return this.filter { resultFilter.filter(it.first, it.second) }
}

fun Collection<Pair<File2, File2>>.left() = this.map { it.first }
fun Collection<Pair<File2, File2>>.right() = this.map { it.second }

fun Collection<SyncFiles.FromTo>.from() = this.map { it.from }
fun Collection<SyncFiles.FromTo>.to() = this.map { it.to }
