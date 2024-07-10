package de.danielscholz.fileSync.actions.sync

import com.google.common.collect.ListMultimap
import de.danielscholz.fileSync.actions.sync.SyncFiles.Companion.commonFileSuffix
import de.danielscholz.fileSync.actions.sync.SyncFiles.Companion.indexedFilesFilePrefix
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.persistence.FileHashEntity
import de.danielscholz.fileSync.persistence.folderMarkerName
import de.danielscholz.fileSync.persistence.readIndexedFiles
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinInstant
import java.io.File


interface CurrentFiles {
    val files: Set<FileEntity>
}

class MutableCurrentFiles(
    override val files: MutableSet<FileEntity>,
) : CurrentFiles


context(MutableFoldersContext, MutableStatisticsContext, CaseSensitiveContext)
fun getCurrentFiles(
    dir: File,
    filter: Filter,
    lastIndexedFiles: Set<FileEntity>,
    lastIndexedFilesDate: LocalDateTime,
    syncName: String,
    processDirCallback: (String) -> Unit,
    now: LocalDateTime,
): MutableCurrentFiles {

    val files = mutableSetOf<FileEntity>()

    val lastIndexedFilesAsMap = lastIndexedFiles.associateBy { Quad(it.folderId, it.name, it.size, it.modified) }
    val lastIndexedFilesAsMultimap by myLazy { lastIndexedFiles.multiAssociateBy { Triple(it.name, it.size, it.modified) } }

    val cancelledIndexingResultFile = File(dir, "$indexedFilesFilePrefix${syncName}_TEMP$commonFileSuffix")

    val cancelledIndexedFiles = if (cancelledIndexingResultFile.isFile) {
        val cancelledIndexedFilesEntity = readIndexedFiles(cancelledIndexingResultFile)!!
        if (cancelledIndexedFilesEntity.runDate > lastIndexedFilesDate) cancelledIndexedFilesEntity.mapToRead(filter) else setOf()
    } else setOf()

    val cancelledIndexedFilesAsMap = cancelledIndexedFiles.associateBy { Quad(it.folderId, it.name, it.size, it.modified) }
    val cancelledIndexedFilesAsMultimap by myLazy { cancelledIndexedFiles.multiAssociateBy { Triple(it.name, it.size, it.modified) } }


    fun process(folderResult: FolderResult, folderId: Long) {

        println("$dir${foldersCtx.getFullPath(folderId)}")
        processDirCallback("$dir${foldersCtx.getFullPath(folderId)}")

        val filteredFiles = folderResult.files
            .filter { filter.fileFilter.excluded(it.path, it.name) == null }

        fun getFilesMovedFromDifferentFolderId(fromMap: ListMultimap<Triple<String, Long, Instant>, FileEntity>): Long? {
            return filteredFiles
                .mapNotNull { file ->
                    val found = fromMap[Triple(file.name, file.size, file.modified)]
                    if (found.size == 1) found.first().folderId else null
                }
                .groupingBy { it } // group by folderId
                .eachCount()
                .maxByOrNull { it.value }
                ?.let { (differentFolderId, numberOfFiles) ->
                    // if >= 66 percent of filteredFiles has the same new folderId
                    if (100 * numberOfFiles / filteredFiles.size >= 66) differentFolderId else null
                }
        }

        val filesMovedFromDifferentFolderId by myLazy { getFilesMovedFromDifferentFolderId(lastIndexedFilesAsMultimap) }
        val filesMovedFromDifferentFolderId2 by myLazy { getFilesMovedFromDifferentFolderId(cancelledIndexedFilesAsMultimap) }

        filteredFiles.forEach { file ->

            val fileHash = supply {
                val key1 = Quad(folderId, file.name, file.size, file.modified)
                val key2 = Triple(file.name, file.size, file.modified)
                cancelledIndexedFilesAsMap[key1]?.fileHash
                    ?: cancelledIndexedFilesAsMultimap[key2]
                        .firstOrNull { it.folderId == filesMovedFromDifferentFolderId2 }
                        ?.fileHash
                    // simple case, file in same location and with same size and modification date:
                    ?: lastIndexedFilesAsMap[key1]?.fileHash
                    // same, but folder renamed:
                    ?: lastIndexedFilesAsMultimap[key2]
                        .firstOrNull { it.folderId == filesMovedFromDifferentFolderId }
                        ?.fileHash
                    // in all other cases: calculate hash
                    ?: file.hash.value?.let {
                        statisticsCtx.hashCalculated++
                        FileHashEntity(java.time.Instant.now().toKotlinInstant(), it)
                    }
            }

            files += FileEntity(
                fileHash = fileHash,
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
            fileHash = null,
            folderId = folderId,
            name = folderMarkerName,
            created = folderMarkerInstant,
            modified = folderMarkerInstant,
            hidden = true,
            size = 0
        )

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

    try {

        process(readDir(dir), foldersCtx.rootFolderId)

    } catch (e: Exception) {
        files.saveIndexedFilesTo(cancelledIndexingResultFile, now)
        throw e
    }

    return MutableCurrentFiles(files)
}