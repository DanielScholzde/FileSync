package de.danielscholz.fileSync.persistence

import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.common.getFileExtension
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.*


sealed interface EntityBase


@Serializable
data class FileHash(
    @SerialName("c")
    val calculated: Instant,
    @SerialName("h")
    val hash: String, // sha1 hash of file content
) : EntityBase {

    init {
        if (hash.isEmpty()) throw Exception()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileHash) return false
        if (hash != other.hash) return false
        return true
    }

    override fun hashCode() = hash.hashCode()
}

@Serializable
data class Folder(
    val id: Long,
    @SerialName("pId")
    val parentFolderId: Long?,
    @SerialName("n")
    val name: String, // last part of the path
    @SerialName("c")
    val children: MutableList<Folder> = mutableListOf(),
) : EntityBase {

    init {
        if (name.isEmpty() && parentFolderId != null) throw Exception()
        if (name.contains('\\')) throw Exception()
        if (name.contains("/")) throw Exception()
    }

    /**
     * includes folder name itself as last node.
     * starts and ends with '/'
     */
    context(FoldersContext)
    val fullPath: String
        get() = (parentFolderId?.let { foldersCtx.get(it)!!.fullPath } ?: "") + name + "/"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Folder) return false
        if (parentFolderId != other.parentFolderId) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        var result = parentFolderId?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        return result
    }
}

/**
 * equals: only Folder + Filename
 */
@Serializable
data class File2(
    @SerialName("fId")
    val folderId: Long,
    @SerialName("n")
    val name: String, // full name incl. extension
    @SerialName("c")
    val created: Instant,
    @SerialName("m")
    val modified: Instant,
    @SerialName("h")
    val hidden: Boolean,
    @SerialName("s")
    val size: Long, // file size in byte
    val hash: FileHash?, // only set if the file is not empty
) : EntityBase {
    init {
        if (size < 0) throw Exception()
        if (name.isEmpty()) throw Exception()
        if (name.contains('\\')) throw Exception()
        if (name.contains("/")) throw Exception()
    }

    @Transient
    val extension: String? = name.getFileExtension()

    context(FoldersContext)
    fun path(): String {
        return foldersCtx.get(folderId)!!.fullPath
    }

    context(FoldersContext)
    fun pathAndName(): String {
        return foldersCtx.get(folderId)!!.fullPath + name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is File2) return false
        if (folderId != other.folderId) return false
        if (name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        var result = folderId.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

@Serializable
data class SyncResult(
    val otherPath: String,
    val runDate: LocalDateTime, // date of index run
    val files: List<File2>,
    val folder: Folder, // root folder (references all other sub folders)
    val failuresOccurred: List<String>
)

@Serializable
data class DeletedFiles(
    val files: List<File2>, // hint: FolderId is always 0
)


@OptIn(ExperimentalSerializationApi::class)
fun readSyncResult(file: File): SyncResult {
    BufferedInputStream(FileInputStream(file)).use {
        return Json.decodeFromStream<SyncResult>(it)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun saveSyncResult(file: File, syncResult: SyncResult) {
    BufferedOutputStream(FileOutputStream(file)).use {
        Json.encodeToStream(syncResult, it)
    }
}


@OptIn(ExperimentalSerializationApi::class)
fun readDeletedFiles(file: File): DeletedFiles {
    BufferedInputStream(FileInputStream(file)).use {
        return Json.decodeFromStream<DeletedFiles>(it)
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun saveDeletedFiles(file: File, deletedFiles: DeletedFiles) {
    BufferedOutputStream(FileOutputStream(file)).use {
        Json.encodeToStream(deletedFiles, it)
    }
}
