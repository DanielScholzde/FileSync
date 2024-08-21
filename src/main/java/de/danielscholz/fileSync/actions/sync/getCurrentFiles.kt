package de.danielscholz.fileSync.actions.sync

import com.google.common.collect.ListMultimap
import de.danielscholz.fileSync.actions.sync.SyncFiles.Companion.commonFileSuffix
import de.danielscholz.fileSync.actions.sync.SyncFiles.Companion.indexedFilesFilePrefix
import de.danielscholz.fileSync.actions.sync.SyncFiles.Companion.syncFilesDir
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


interface ILayer {
    val indexedFilesAsMap: Map<Quad<Long, String, Long, Instant>, FileEntity>
    val indexedFilesAsMultimap: ListMultimap<Triple<String, Long, Instant>, FileEntity>
}

class Layer(private val indexedFiles: Set<FileEntity>, val indexDate: LocalDateTime) : ILayer {
    override val indexedFilesAsMap = indexedFiles.associateBy { Quad(it.folderId, it.name, it.size, it.modified) }
    override val indexedFilesAsMultimap by myLazy { indexedFiles.multiAssociateBy { Triple(it.name, it.size, it.modified) } }
}

class LayerExtended(private val layer: Layer, filesMovedFromDifferentFolderId: Lazy<Long?>) : ILayer by layer {
    val filesMovedFromDifferentFolderId: Long? by filesMovedFromDifferentFolderId
}


context(MutableFoldersContext, MutableStatisticsContext, CaseSensitiveContext)
fun getCurrentFiles(
    dir: File,
    filter: Filter,
    lastIndexedFiles: Set<FileEntity>,
    lastIndexedFilesDate: LocalDateTime,
    syncName: String,
    considerOtherIndexedFilesWithSyncName: String?,
    processDirCallback: (String) -> Unit,
    now: LocalDateTime,
    fs: FileSystemEncryption
): MutableCurrentFiles {

    val files = mutableSetOf<FileEntity>()


    val indexedFilesFromOtherSync = considerOtherIndexedFilesWithSyncName?.let {
        val indexResultFile = File(dir, "$syncFilesDir/$indexedFilesFilePrefix${considerOtherIndexedFilesWithSyncName}$commonFileSuffix")
        if (indexResultFile.isFile) {
            val indexedFilesEntity = readIndexedFiles(indexResultFile)!!
            indexedFilesEntity.mapToRead(filter) to indexedFilesEntity.runDate
        } else null
    }

    val cancelledIndexingResultFile = File(dir, "$syncFilesDir/$indexedFilesFilePrefix${syncName}_TEMP$commonFileSuffix")

    val cancelledIndexedFiles = if (cancelledIndexingResultFile.isFile) {
        val cancelledIndexedFilesEntity = readIndexedFiles(cancelledIndexingResultFile)!!
        if (cancelledIndexedFilesEntity.runDate > lastIndexedFilesDate) cancelledIndexedFilesEntity.mapToRead(filter) to cancelledIndexedFilesEntity.runDate else null
    } else null


    val layers = listOfNotNull(
        cancelledIndexedFiles?.let { Layer(it.first, it.second) },
        indexedFilesFromOtherSync?.let { Layer(it.first, it.second) },
        Layer(lastIndexedFiles, lastIndexedFilesDate),
    ).sortedByDescending { it.indexDate }


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

        val layersExtended = layers.map { LayerExtended(it, myLazy { getFilesMovedFromDifferentFolderId(it.indexedFilesAsMultimap) }) }

        filteredFiles.forEach { file ->

            val fileHash = supply {
                val key1 = Quad(folderId, file.name, file.size, file.modified)
                val key2 = Triple(file.name, file.size, file.modified)

                for (layer in layersExtended) {
                    val result =
                        // simple case, file in same location and with same size and modification date:
                        layer.indexedFilesAsMap[key1]?.fileHash
                        // same, but folder renamed:
                            ?: layer.indexedFilesAsMultimap[key2]
                                .firstOrNull { it.folderId == layer.filesMovedFromDifferentFolderId }
                                ?.fileHash
                            ?: continue // if not found, continue with next layer
                    return@supply result
                }
                // if not found within any layer: calculate hash
                file.hash.value?.let {
                    statisticsCtx.filesHashCalculatedCount++
                    statisticsCtx.filesHashCalculatedSize += file.size
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
            statisticsCtx.filesCount++

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
                statisticsCtx.foldersCount++
                process(it.content(), folder.id)
            }
    }

    try {

        process(readDir(dir, fs = fs), foldersCtx.rootFolderId)

    } catch (e: Exception) {
        files.saveIndexedFilesTo(cancelledIndexingResultFile, now)
        throw e
    }

    return MutableCurrentFiles(files)
}