package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.common.MutableFoldersContext
import de.danielscholz.fileSync.persistence.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import java.io.File
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

val PAST_LOCAL_DATE_TIME = LocalDateTime(0, 1, 1, 0, 0, 0)

context(FoldersContext)
fun Set<FileEntity>.saveIndexedFilesTo(file: File, dateTime: LocalDateTime) {
    saveIndexedFiles(
        file,
        IndexedFilesEntity(
            runDate = dateTime,
            files = this,
            rootFolder = foldersCtx.get(foldersCtx.rootFolderId).stripUnusedFolder(this.usedFolderIds()),
        )
    )
}

context(MutableFoldersContext)
fun FilesAndFolder.mapToRead(filter: Filter): Set<FileEntity> {
    val mapping = mutableMapOf<Long, Long>()
    mapping[foldersCtx.rootFolderId] = foldersCtx.rootFolderId

    fun sync(folder: FolderEntity, parentFolderId: Long) {
        if (filter.folderFilter.excluded(foldersCtx.getFullPath(parentFolderId) + folder.name + "/", folder.name) != null) {
            return
        }

        val folderMapped = foldersCtx.getOrCreate(folder.name, parentFolderId)
        mapping[folder.id] = folderMapped.id
        folder.children.forEach { childFolder ->
            sync(childFolder, folderMapped.id)
        }
    }

    foldersCtx.check()

    this.rootFolder.children.forEach { childFolder ->
        sync(childFolder, foldersCtx.rootFolderId)
    }

    foldersCtx.check()

    return this.files.mapNotNull { file ->
        mapping[file.folderId]?.let { folderId ->
            if (file.folderId != folderId) file.copy(folderId = folderId) else file
        }
    }.toSet()
}


@OptIn(ExperimentalContracts::class)
fun execute(block1: () -> Unit, block2: () -> Unit, parallel: Boolean = true) {
    contract {
        callsInPlace(block1, InvocationKind.EXACTLY_ONCE)
        callsInPlace(block2, InvocationKind.EXACTLY_ONCE)
    }
    val blocks = listOf(block1, block2)
    //println("Thread: " + Thread.currentThread().name)
    if (parallel) {
        runBlocking(Dispatchers.IO) {
            blocks
                .map {
                    async { it() }
                }
                .forEach {
                    it.await()
                }
        }
    } else {
        blocks.forEach { it() }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <R> supply(block: () -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block()
}
