package de.danielscholz.fileSync.actions

import de.danielscholz.fileSync.common.mutableListMultimapOf
import de.danielscholz.fileSync.common.set
import de.danielscholz.fileSync.persistence.Folder


interface Folders {
    val rootFolderId: Long
    val folders: Map<Long, Folder>
}

interface FoldersMutable : Folders {
    fun getOrCreate(name: String, parentFolderId: Long): Folder
}

class FoldersImpl : FoldersMutable {

    override val rootFolderId = 0L

    private var folderId = 0L

    private val _folders = mutableMapOf<Long, Folder>()
    override val folders: Map<Long, Folder>
        get() = _folders

    private val foldersByParent = mutableListMultimapOf<Long, Folder>()

    init {
        _folders[rootFolderId] = Folder(rootFolderId, null, "")
    }

    override fun getOrCreate(name: String, parentFolderId: Long): Folder {
        if (!folders.containsKey(parentFolderId)) {
            throw IllegalStateException()
        }

        foldersByParent[parentFolderId].firstOrNull { it.name == name }?.let {
            return it
        }
        val id = ++folderId
        val folder = Folder(id, parentFolderId, name)
        _folders[id] = folder
        foldersByParent[parentFolderId] = folder
        _folders[parentFolderId]!!.children += folder
        return folder
    }
}