package de.danielscholz.fileSync.actions

import de.danielscholz.fileSync.common.mutableListMultimapOf
import de.danielscholz.fileSync.common.set
import de.danielscholz.fileSync.persistence.Folder


interface Folders {
    val rootFolderId: Long

    fun get(id: Long): Folder

    /**
     * includes folder name itself as last node.
     * starts and ends with '/'
     */
    fun getFullPath(id: Long): String

    /**
     * includes folder name itself as last node.
     * starts and ends with '/'
     */
    fun getFullPath(folder: Folder): String

    fun getAll(): List<Folder>
}

interface FoldersMutable : Folders {
    fun getOrCreate(name: String, parentFolderId: Long): Folder
}

class FoldersImpl : FoldersMutable {

    override val rootFolderId = 0L

    private var maxAssignedFolderId = 0L

    private val folders = mutableMapOf<Long, Folder>()

    private val foldersByParent = mutableListMultimapOf<Long, Folder>()
    private val foldersFullPath = mutableMapOf<Long, String>()

    init {
        folders[rootFolderId] = Folder(rootFolderId, null, "")
    }

    @Synchronized
    override fun get(id: Long): Folder {
        return folders[id]!!
    }

    @Synchronized
    override fun getAll(): List<Folder> {
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

    override fun getFullPath(folder: Folder): String {
        return (folder.parentFolderId?.let { getFullPath(it) } ?: "") + folder.name + "/"
    }

    @Synchronized
    override fun getOrCreate(name: String, parentFolderId: Long): Folder {
        if (!folders.containsKey(parentFolderId)) {
            throw IllegalStateException()
        }

        foldersByParent[parentFolderId].firstOrNull { it.name == name }?.let {
            return it
        }
        val id = ++maxAssignedFolderId
        val folder = Folder(id, parentFolderId, name)
        folders[id] = folder
        foldersByParent[parentFolderId] = folder
        folders[parentFolderId]!!.children += folder
        return folder
    }
}