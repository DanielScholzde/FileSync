package de.danielscholz.fileSync.actions

import de.danielscholz.fileSync.common.mutableListMultimapOf
import de.danielscholz.fileSync.common.set
import de.danielscholz.fileSync.persistence.Folder


interface Folders {
    val rootFolderId: Long
    fun get(id: Long): Folder?
}

interface FoldersMutable : Folders {
    fun getOrCreate(name: String, parentFolderId: Long): Folder
}

class FoldersImpl : FoldersMutable {

    override val rootFolderId = 0L

    private var maxAssignedFolderId = 0L

    private val folders = mutableMapOf<Long, Folder>()

    private val foldersByParent = mutableListMultimapOf<Long, Folder>()

    init {
        folders[rootFolderId] = Folder(rootFolderId, null, "")
    }

    @Synchronized
    override fun get(id: Long): Folder? {
        return folders[id]
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