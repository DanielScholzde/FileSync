package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.persistence.File2
import de.danielscholz.fileSync.persistence.FileHash
import de.danielscholz.fileSync.persistence.folderMarkerName
import kotlinx.datetime.toKotlinInstant
import java.io.File


class CurrentFilesResult(
    val files: List<File2>,
    val folderRenamed: Map</* from folderId */ Long, /* to folderId */ Long>,
    val folderPathRenamed: Map</* from folderId */ Long, /* to folderId */ Long>,
)


context(MutableFoldersContext)
fun getCurrentFiles(dir: File, filter: Filter, lastSyncResult: List<File2>, statistics: Statistics): CurrentFilesResult {

    val files = mutableListOf<File2>()
    val folderRenamed = mutableMapOf</* from folderId */ Long, /* to folderId */ Long>()
    val folderPathRenamed = mutableMapOf</* from folderId */ Long, /* to folderId */ Long>()

    val lastSyncMap1 = lastSyncResult.associateBy { Quad(it.folderId, it.name, it.size, it.modified) }
    val lastSyncMap2 by myLazy { lastSyncResult.multiAssociateBy { Triple(it.name, it.size, it.modified) } }

    fun process(folderResult: FolderResult, folderId: Long) {

        val filteredFiles = folderResult.files
            .filter { filter.fileFilter.excluded(it.path, it.name) == null }

        val filesMovedFromDifferentFolderId = myLazy {
            filteredFiles
                .mapNotNull { file -> lastSyncMap2[Triple(file.name, file.size, file.modified)].let { if (it.size == 1) it.first().folderId else null } }
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
                ?: lastSyncMap2[Triple(file.name, file.size, file.modified)]
                    .firstOrNull { it.folderId == filesMovedFromDifferentFolderId.value }
                    ?.hash
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
            statistics.files++

            testIfCancel()
        }

        // add marker for an existing folder
        files += File2(
            hash = null,
            folderId = folderId,
            name = folderMarkerName,
            created = folderMarkerInstant,
            modified = folderMarkerInstant,
            hidden = true,
            size = 0
        )

        if (filesMovedFromDifferentFolderId.isInitialized()) {
            val fromFolderId = filesMovedFromDifferentFolderId.value
            if (fromFolderId != null) {
                if (foldersCtx.get(fromFolderId).name != foldersCtx.get(folderId).name) {
                    println("folder renamed: " + foldersCtx.getFullPath(fromFolderId) + " -> " + foldersCtx.getFullPath(folderId))
                    folderRenamed[fromFolderId] = folderId
                }
                folderPathRenamed[fromFolderId] = folderId
            }
        }

        folderResult.folders
            .filter {
                val excludedBy = filter.folderFilter.excluded(it.fullPath, it.name)
                if (excludedBy == ExcludedBy.USER) println("$dir${it.fullPath} (excluded)")
                excludedBy == null
            }
            .forEach {
                val folder = foldersCtx.getOrCreate(it.name, folderId)
                statistics.folders++
                process(it.content(), folder.id)
            }
    }

    process(readDir(dir), foldersCtx.rootFolderId)

    return CurrentFilesResult(files, folderRenamed, folderPathRenamed)
}