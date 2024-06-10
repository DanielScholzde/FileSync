package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.FolderResult
import de.danielscholz.fileSync.common.MutableFoldersContext
import de.danielscholz.fileSync.common.readDir
import de.danielscholz.fileSync.common.testIfCancel
import de.danielscholz.fileSync.persistence.File2
import de.danielscholz.fileSync.persistence.FileHash
import kotlinx.datetime.toKotlinInstant
import java.io.File


context(MutableFoldersContext)
fun getCurrentFiles(dir: File, filter: Filter, lastSyncResult: List<File2>): List<File2> {

    val files = mutableListOf<File2>()

    val lastSyncMap = lastSyncResult.associateBy { (it.folderId to it.name) }

    fun process(folderResult: FolderResult, folderId: Long) {
        folderResult.files
            .filter { filter.fileFilter.excluded(it.path, it.name) == null }
            .forEach { file ->

                val lastCalculatedHash = lastSyncMap[folderId to file.name]?.let { lastIndexedFile ->
                    if (lastIndexedFile.size == file.size && lastIndexedFile.modified == file.modified) {
                        lastIndexedFile.hash
                    } else null
                }

                files += File2(
                    hash = lastCalculatedHash ?: file.hash.value?.let { FileHash(java.time.Instant.now().toKotlinInstant(), it) },
                    folderId = folderId,
                    name = file.name,
                    created = file.created,
                    modified = file.modified,
                    hidden = file.hidden,
                    size = file.size
                )

                testIfCancel()
            }

        folderResult.folders
            .filter {
                val excludedBy = filter.folderFilter.excluded(it.fullPath, it.name)
                if (excludedBy == ExcludedBy.USER) println("$dir${it.fullPath} (excluded)")
                excludedBy == null
            }
            .forEach {
                val folder = foldersCtx.getOrCreate(it.name, folderId)
                process(it.content(), folder.id)
            }
    }

    process(readDir(dir), foldersCtx.rootFolderId)

    return files
}