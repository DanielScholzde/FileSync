package de.danielscholz.fileSync.actions

import de.danielscholz.fileSync.common.mutableListMultimapOf
import de.danielscholz.fileSync.common.set
import de.danielscholz.fileSync.persistence.FolderEntity


interface Folders {
    val rootFolderId: Long

    fun get(id: Long): FolderEntity

    /**
     * includes folder name itself as last node.
     * starts and ends with '/'
     */
    fun getFullPath(id: Long): String

    /**
     * includes folder name itself as last node.
     * starts and ends with '/'
     */
    fun getFullPath(folder: FolderEntity): String

    fun getAll(): List<FolderEntity>
}

interface FoldersMutable : Folders {
    fun getOrCreate(name: String, parentFolderId: Long): FolderEntity
}

class FoldersImpl : FoldersMutable {

    override val rootFolderId = 0L

    private var maxAssignedFolderId = 0L

    private val folders = mutableMapOf<Long, FolderEntity>()

    private val foldersByParent = mutableListMultimapOf<Long, FolderEntity>()
    private val foldersFullPath = mutableMapOf<Long, String>()

    init {
        folders[rootFolderId] = FolderEntity(rootFolderId, null, "")
    }

    @Synchronized
    override fun get(id: Long): FolderEntity {
        return folders[id]!!
    }

    @Synchronized
    override fun getAll(): List<FolderEntity> {
        return folders.values.toList()
    }

    @Synchronized
    override fun getFullPath(id: Long): String {

        fun getFullPathIntern(id: Long): String {
            return foldersFullPath.getOrPut(id) {
                val folder = folders[id]!!
                (folder.parentFolderId?.let { getFullPathIntern(it) } ?: "") + folder.name + "/"
            }
        }
        return getFullPathIntern(id)
    }

    override fun getFullPath(folder: FolderEntity): String {
        return (folder.parentFolderId?.let { getFullPath(it) } ?: "") + folder.name + "/"
    }

    @Synchronized
    override fun getOrCreate(name: String, parentFolderId: Long): FolderEntity {
        if (!folders.containsKey(parentFolderId)) {
            throw IllegalStateException()
        }

        foldersByParent[parentFolderId].firstOrNull { it.name == name }?.let {
            return it
        }
        val id = ++maxAssignedFolderId
        val folder = FolderEntity(id, parentFolderId, name)
        folders[id] = folder
        foldersByParent[parentFolderId] = folder
        folders[parentFolderId]!!.children += folder
        return folder
    }
}