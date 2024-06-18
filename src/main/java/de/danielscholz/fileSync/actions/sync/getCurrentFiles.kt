package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.persistence.FileHashEntity
import de.danielscholz.fileSync.persistence.folderMarkerName
import kotlinx.datetime.toKotlinInstant
import java.io.File


interface CurrentFiles {
    val files: Set<FileEntity>
}

class MutableCurrentFiles(
    override val files: MutableSet<FileEntity>,
) : CurrentFiles


context(MutableFoldersContext, MutableStatisticsContext, CaseSensitiveContext)
fun getCurrentFiles(dir: File, filter: Filter, lastIndexedFiles: Set<FileEntity>): MutableCurrentFiles {

    val files = mutableSetOf<FileEntity>()
    val folderRenamed = mutableMapOf</* from folderId */ Long, /* to folderId */ Long>()
    val folderPathRenamed = mutableMapOf</* from folderId */ Long, /* to folderId */ Long>()

    val lastIndexedFilesAsMap1 = lastIndexedFiles.associateBy { Quad(it.folderId, it.name, it.size, it.modified) }
    val lastIndexedFilesAsMultiMap2 by myLazy { lastIndexedFiles.multiAssociateBy { Triple(it.name, it.size, it.modified) } }

    fun process(folderResult: FolderResult, folderId: Long) {

        println("$dir${foldersCtx.getFullPath(folderId)}")

        val filteredFiles = folderResult.files
            .filter { filter.fileFilter.excluded(it.path, it.name) == null }

        val filesMovedFromDifferentFolderId = myLazy {
            filteredFiles
                .mapNotNull { file -> lastIndexedFilesAsMultiMap2[Triple(file.name, file.size, file.modified)].let { if (it.size == 1) it.first().folderId else null } }
                .groupingBy { it } // group by folderId
                .eachCount()
                .maxByOrNull { it.value }
                ?.let { (differentFolderId, numberOfFiles) ->
                    // if >= 66 percent of filteredFiles has the same new folderId
                    if (100 * numberOfFiles / filteredFiles.size >= 66) differentFolderId else null
                }
        }

        filteredFiles.forEach { file ->

            val hash =
                // simple case, file in same location and with same size and modification date:
                lastIndexedFilesAsMap1[Quad(folderId, file.name, file.size, file.modified)]?.hash
                // same, but folder renamed:
                    ?: lastIndexedFilesAsMultiMap2[Triple(file.name, file.size, file.modified)]
                        .firstOrNull { it.folderId == filesMovedFromDifferentFolderId.value }
                        ?.hash
                    // in all other cases: calculate hash
                    ?: file.hash.value?.let {
                        statisticsCtx.hashCalculated++
                        FileHashEntity(java.time.Instant.now().toKotlinInstant(), it)
                    }

            files += FileEntity(
                hash = hash,
                folderId = folderId,
                name = file.name,
                created = file.created,
                modified = file.modified,
                hidden = file.hidden,
                size = file.size
            )
            statisticsCtx.files++

            testIfCancel()
        }

        // add marker for an existing folder
        files += FileEntity(
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
                if (isCaseSensitive) {
                    if (foldersCtx.get(fromFolderId).name != foldersCtx.get(folderId).name) {
                        println("folder renamed: " + foldersCtx.getFullPath(fromFolderId) + " -> " + foldersCtx.getFullPath(folderId))
                        folderRenamed[fromFolderId] = folderId
                    }
                } else {
                    if (foldersCtx.get(fromFolderId).name.lowercase() != foldersCtx.get(folderId).name.lowercase()) {
                        println("folder renamed: " + foldersCtx.getFullPath(fromFolderId) + " -> " + foldersCtx.getFullPath(folderId))
                        folderRenamed[fromFolderId] = folderId
                    }
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
                statisticsCtx.folders++
                process(it.content(), folder.id)
            }
    }

    process(readDir(dir), foldersCtx.rootFolderId)

    return MutableCurrentFiles(files)
}