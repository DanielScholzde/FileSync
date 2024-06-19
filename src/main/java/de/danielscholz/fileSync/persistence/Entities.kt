package de.danielscholz.fileSync.persistence

import de.danielscholz.fileSync.common.FoldersContext
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


/**
 * Immutable!
 */
@Serializable
data class FileHashEntity(
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
        if (other !is FileHashEntity) return false
        if (hash != other.hash) return false
        return true
    }

    override fun hashCode() = hash.hashCode()
}

@Serializable
data class FolderEntity(
    val id: Long,
    @SerialName("pId")
    val parentFolderId: Long?,
    @SerialName("n")
    val name: String, // last part of the path
    @SerialName("c")
    val children: MutableList<FolderEntity> = mutableListOf(),
) : EntityBase {

    init {
        if (name.isEmpty() && parentFolderId != null) throw Exception()
        if (name.contains('\\')) throw Exception()
        if (name.contains("/")) throw Exception()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FolderEntity) return false
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

fun FolderEntity.stripUnusedFolder(usedFolderIds: Set<Long>): FolderEntity {
    return this.copy(
        children = this.children
            .filter { it.id in usedFolderIds }
            .map { it.stripUnusedFolder(usedFolderIds) }
            .toMutableList()
    )
}

/**
 * Immutable!
 * equals: only Folder + Filename
 */
@Serializable
data class FileEntity(
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
    val hash: FileHashEntity?, // only set if the file is not empty
) : EntityBase {
    init {
        if (size < 0) throw Exception()
        if (name.isEmpty()) throw Exception()
        if (name.contains('\\')) throw Exception()
        if (name.contains("/")) throw Exception()
    }

    @Transient
    val nameLowercase: String = name.lowercase()

    context(FoldersContext)
    fun path(): String {
        return foldersCtx.getFullPath(folderId)
    }

    context(FoldersContext)
    fun pathAndName(): String {
        return foldersCtx.getFullPath(folderId) + name
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileEntity) return false
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

const val folderMarkerName = ".@folderMarker@"
val FileEntity.isFolderMarker
    get() = this.size == 0L && this.name == folderMarkerName

fun Collection<FileEntity>.usedFolderIds() = this.asSequence().filter { it.isFolderMarker }.map { it.folderId }.toSet()


interface FilesAndFolder {
    val files: Set<FileEntity>
    val rootFolder: FolderEntity
}

@Serializable
data class IndexedFilesEntity(
    val runDate: LocalDateTime, // date of index run
    override val files: Set<FileEntity>,
    @SerialName("folder")
    override val rootFolder: FolderEntity, // root folder (references all other sub folders)
) : FilesAndFolder

@Serializable
data class SyncResultEntity(
    val sourcePath: String,
    val targetPath: String,
    val runDate: LocalDateTime, // date of index run
    val failuresOccurred: List<String>,
    override val files: Set<FileEntity>,
    @SerialName("folder")
    override val rootFolder: FolderEntity, // root folder (references all other sub folders)
) : FilesAndFolder


@Serializable
data class DeletedFileEntity(
    val hash: String?,
    val name: String,
)

@Serializable
data class DeletedFilesEntity(
    val files: Set<DeletedFileEntity>,
)


@OptIn(ExperimentalSerializationApi::class)
fun readIndexedFiles(file: File): IndexedFilesEntity? {
    if (file.exists()) {
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
    if (file.exists()) {
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
    if (file.exists()) {
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
