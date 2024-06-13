package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.persistence.File2
import de.danielscholz.fileSync.persistence.FileHash
import kotlinx.datetime.toKotlinInstant
import java.io.File


context(MutableFoldersContext)
fun getCurrentFiles(dir: File, filter: Filter, lastSyncResult: List<File2>, statistics: Statistics): List<File2> {

    val files = mutableListOf<File2>()

    val lastSyncMap1 = lastSyncResult.associateBy { Quad(it.folderId, it.name, it.size, it.modified) }
    val lastSyncMap2 by myLazy { lastSyncResult.associateBy { Triple(it.name, it.size, it.modified) } }

    fun process(folderResult: FolderResult, folderId: Long) {

        val filteredFiles = folderResult.files
            .filter { filter.fileFilter.excluded(it.path, it.name) == null }

        val filesMovedToDifferentFolderId by myLazy {
            filteredFiles
                .mapNotNull { file -> lastSyncMap2[Triple(file.name, file.size, file.modified)]?.folderId }
                .groupingBy { it }
                .eachCount()
                .entries
                .maxByOrNull { it.value }
                ?.let {
                    val (differentFolderId, numberOfFiles) = it
                    // if >= 66 percent of filteredFiles has the same new folderId
                    if (100 * numberOfFiles / filteredFiles.size >= 66) differentFolderId else null
                }
        }

        filteredFiles.forEach { file ->

            val hash = lastSyncMap1[Quad(folderId, file.name, file.size, file.modified)]?.hash
                ?: let {
                    lastSyncMap2[Triple(file.name, file.size, file.modified)]?.let {
                        if (it.folderId == filesMovedToDifferentFolderId) it.hash else null
                    }
                }
                ?: file.hash.value?.let {
                    statistics.hashCalculated++
                    FileHash(java.time.Instant.now().toKotlinInstant(), it)
                }

            files += File2(
                hash = hash,
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