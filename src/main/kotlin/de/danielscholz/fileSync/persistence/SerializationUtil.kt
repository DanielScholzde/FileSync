package de.danielscholz.fileSync.persistence

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.*


@OptIn(ExperimentalSerializationApi::class)
fun readIndexedFiles(file: File): IndexedFilesEntity? {
    if (file.isFile()) {
        BufferedInputStream(FileInputStream(file)).use {
            return Json.decodeFromStream<IndexedFilesEntity>(it)
        }
    }
    return null
}

@OptIn(ExperimentalSerializationApi::class)
fun saveIndexedFiles(file: File, syncResult: IndexedFilesEntity) {
    BufferedOutputStream(FileOutputStream(file)).use {
        Json.encodeToStream(syncResult, it)
    }
}


@OptIn(ExperimentalSerializationApi::class)
fun readSyncResult(file: File): SyncResultEntity? {
    if (file.isFile()) {
        BufferedInputStream(FileInputStream(file)).use {
            return Json.decodeFromStream<SyncResultEntity>(it)
        }
    }
    return null
}

@OptIn(ExperimentalSerializationApi::class)
fun saveSyncResult(file: File, syncResult: SyncResultEntity) {
    BufferedOutputStream(FileOutputStream(file)).use {
        Json.encodeToStream(syncResult, it)
    }
}


@OptIn(ExperimentalSerializationApi::class)
fun readDeletedFiles(file: File): DeletedFilesEntity? {
    if (file.isFile()) {
        BufferedInputStream(FileInputStream(file)).use {
            return Json.decodeFromStream<DeletedFilesEntity>(it)
        }
    }
    return null
}

@OptIn(ExperimentalSerializationApi::class)
fun saveDeletedFiles(file: File, deletedFiles: DeletedFilesEntity) {
    BufferedOutputStream(FileOutputStream(file)).use {
        Json.encodeToStream(deletedFiles, it)
    }
}

//private fun setHidden(file: File) {
//    if (file.isHidden()) return
//    try {
//        Files.setAttribute(file.toPath(), "dos:hidden", true)
//    } catch (e: Exception) {
//        // ignore
//    }
//}