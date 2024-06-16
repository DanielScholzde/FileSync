package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.persistence.FileEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


fun Collection<Pair<FileEntity, FileEntity>>.leftSide() = this.map { it.first }
fun Collection<Pair<FileEntity, FileEntity>>.rightSide() = this.map { it.second }

@JvmName("filesAdd")
fun Collection<Addition>.files() = this.map { it.file }

@JvmName("filesDel")
fun Collection<Deletion>.files() = this.map { it.file }

fun <T : FileEntity?, R : FileEntity?> Collection<IChange<T, R>>.from() = this.map { it.from }
fun <T : FileEntity?, R : FileEntity?> Collection<IChange<T, R>>.to() = this.map { it.to }


val folderMarkerInstant = LocalDateTime(0, 1, 1, 0, 0).toInstant(UtcOffset.ZERO)


@OptIn(ExperimentalContracts::class)
fun execute(block1: () -> Unit, block2: () -> Unit, parallel: Boolean = true) {
    contract {
        callsInPlace(block1, InvocationKind.EXACTLY_ONCE)
        callsInPlace(block2, InvocationKind.EXACTLY_ONCE)
    }
    val blocks = listOf(block1, block2)
    if (parallel) {
        runBlocking {
            blocks.map { async { it() } }.forEach { it.await() }
        }
    } else {
        blocks.forEach { it() }
    }
}