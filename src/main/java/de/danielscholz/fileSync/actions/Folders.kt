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
     * Depth of folder. Starts with 0 for the root folder
     */
    fun getDepth(id: Long): Int

    fun getAll(): List<FolderEntity>

    fun check()
}


class MutableFolders : Folders {

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
                val folder = folders[id] ?: throw Exception("Folder with id $id not found!")
                (folder.parentFolderId?.let { getFullPathIntern(it) } ?: "") + folder.name + "/"
            }
        }
        return getFullPathIntern(id)
    }

    override fun getDepth(id: Long): Int {
        return getFullPath(id).count { it == '/' } - 1
    }

    @Synchronized
    fun getOrCreate(name: String, parentFolderId: Long): FolderEntity {
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

    @Synchronized
    override fun check() {
        folders.entries.forEach {
            if (it.key != it.value.id) throw Exception()
            val parentFolderId = it.value.parentFolderId
            if (parentFolderId != null) {
                if (folders[parentFolderId] == null) throw Exception()
                if (it.value !in foldersByParent[parentFolderId]) throw Exception()
            }
        }
    }
}