package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.persistence.File2
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant


fun Collection<Pair<File2, File2>>.leftSide() = this.map { it.first }
fun Collection<Pair<File2, File2>>.rightSide() = this.map { it.second }

@JvmName("filesAdd")
fun Collection<Addition>.files() = this.map { it.file }

@JvmName("filesDel")
fun Collection<Deletion>.files() = this.map { it.file }

fun <T : File2?, R : File2?> Collection<IChange<T, R>>.from() = this.map { it.from }
fun <T : File2?, R : File2?> Collection<IChange<T, R>>.to() = this.map { it.to }


val folderMarkerInstant = LocalDateTime(0, 1, 1, 0, 0).toInstant(UtcOffset.ZERO)
