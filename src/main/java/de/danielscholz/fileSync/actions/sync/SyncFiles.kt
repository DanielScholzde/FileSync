package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.FoldersImpl
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.MatchMode.HASH
import de.danielscholz.fileSync.matching.equalsBy
import de.danielscholz.fileSync.persistence.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDateTime
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.LocalDateTime
import kotlin.collections.set


class SyncFiles(private val syncFilesParams: SyncFilesParams) {

    private val backupDir = ".syncFilesHistory"
    private val lockfileName = ".syncFiles_lockfile"
    private val indexedFilesFilePrefix = ".syncFilesIndex_"
    private val indexedFilesFileSuffix = ".jsn"
    private val syncResultFilePrefix = ".syncFilesResult_"
    private val syncResultFileSuffix = ".jsn"
    private val deletedFilesFilePrefix = ".deletedFiles"
    private val deletedFilesFileSuffix = ".jsn"

    fun sync(sourceDir: File, targetDir: File, filter: Filter) {
        guardWithLockFile(File(syncFilesParams.lockfileSourceDir ?: sourceDir, lockfileName)) {
            guardWithLockFile(File(syncFilesParams.lockfileTargetDir ?: targetDir, lockfileName)) {
                syncIntern(sourceDir, targetDir, filter)
            }
        }
    }

    private fun syncIntern(sourceDir: File, targetDir: File, filter: Filter) {
        println("Source dir: $sourceDir")
        println("Target dir: $targetDir\n")

        val now = LocalDateTime.now().ignoreMillis()
        val dateTimeStr = now.toString().replace(":", "").replace("T", " ")
        val changedDir = "$backupDir/modified/$dateTimeStr"
        val deletedDir = "$backupDir/deleted/$dateTimeStr"


        val caseSensitiveContext = CaseSensitiveContext(
            (isCaseSensitiveFileSystem(sourceDir) ?: throw Exception("Unable to determine if filesystem $sourceDir is case sensitive!")) &&
                    (isCaseSensitiveFileSystem(targetDir) ?: throw Exception("Unable to determine if filesystem $targetDir is case sensitive!"))
        )

        val syncName = syncFilesParams.syncName ?: (sourceDir.canonicalPath.toString() + "|" + targetDir.canonicalPath.toString()).hashCode().toString()

        val indexedFilesFileSource = File(sourceDir, "$indexedFilesFilePrefix$syncName$indexedFilesFileSuffix")
        val indexedFilesFileTarget = File(targetDir, "$indexedFilesFilePrefix$syncName$indexedFilesFileSuffix")
        val syncResultFile = File(sourceDir, "$syncResultFilePrefix$syncName$syncResultFileSuffix")
        val deletedFilesFileSource = File(sourceDir, "$deletedFilesFilePrefix$deletedFilesFileSuffix")
        val deletedFilesFileTarget = File(targetDir, "$deletedFilesFilePrefix$deletedFilesFileSuffix")

        val lastSyncResult = if (syncResultFile.exists()) readSyncResult(syncResultFile) else null

        var deletedFiles = let {
            val deletedFilesSource = if (deletedFilesFileSource.exists()) readDeletedFiles(deletedFilesFileSource) else null
            val deletedFilesTarget = if (deletedFilesFileTarget.exists()) readDeletedFiles(deletedFilesFileTarget) else null

            equalsBy(HASH) {
                (deletedFilesSource?.files ?: listOf()) + (deletedFilesTarget?.files ?: listOf())
            }.toList()
        }

        @Suppress("NAME_SHADOWING")
        val filter = Filter(
            fileFilter = { path, fileName ->
                if (fileName.startsWith(indexedFilesFilePrefix) && fileName.endsWith(indexedFilesFileSuffix) ||
                    fileName.startsWith(syncResultFilePrefix) && fileName.endsWith(syncResultFileSuffix) ||
                    fileName.startsWith(deletedFilesFilePrefix) && fileName.endsWith(deletedFilesFileSuffix) ||
                    fileName == lockfileName
                ) {
                    ExcludedBy.SYSTEM
                } else {
                    filter.fileFilter.excluded(path, fileName)
                }
            },
            folderFilter = { fullPath, folderName ->
                if (folderName == backupDir) {
                    ExcludedBy.SYSTEM
                } else {
                    filter.folderFilter.excluded(fullPath, folderName)
                }
            }
        )


        val sourceChanges: MutableChanges
        val targetChanges: MutableChanges
        val syncResultFiles: MutableSet<FileEntity>

        val sourceStatistics = Statistics()
        val targetStatistics = Statistics()

        val folders = FoldersImpl()

        with(MutableFoldersContext(folders)) {
            val lastIndexedFilesSource = if (indexedFilesFileSource.exists()) readIndexedFiles(indexedFilesFileSource) else null
            val lastIndexedFilesTarget = if (indexedFilesFileTarget.exists()) readIndexedFiles(indexedFilesFileTarget) else null
            val lastIndexedFilesSourceFiles = lastIndexedFilesSource?.mapToRead(filter) ?: listOf()
            val lastIndexedFilesTargetFiles = lastIndexedFilesTarget?.mapToRead(filter) ?: listOf()

            val lastSyncResultFiles = lastSyncResult?.mapToRead(filter) ?: listOf()
            syncResultFiles = lastSyncResultFiles.toMutableSet()
            if (lastSyncResultFiles.size != syncResultFiles.size) throw Exception()

            if (syncFilesParams.parallelIndexing) {
                runBlocking {
                    val sourceChangesFuture = async { getChanges(sourceDir, lastSyncResultFiles, lastIndexedFilesSourceFiles, filter, sourceStatistics) }
                    val targetChangesFuture = async { getChanges(targetDir, lastSyncResultFiles, lastIndexedFilesTargetFiles, filter, targetStatistics) }
                    sourceChanges = sourceChangesFuture.await()
                    targetChanges = targetChangesFuture.await()
                }
            } else {
                sourceChanges = getChanges(sourceDir, lastSyncResultFiles, lastIndexedFilesSourceFiles, filter, sourceStatistics)
                targetChanges = getChanges(targetDir, lastSyncResultFiles, lastIndexedFilesTargetFiles, filter, targetStatistics)
            }
        }

        println("Files / Folders (sourceDir): ${sourceStatistics.files} / ${sourceStatistics.folders}")
        println("Files / Folders (targetDir): ${targetStatistics.files} / ${targetStatistics.folders}")
        if (sourceStatistics.files > 0) {
            println("Hash reused (sourceDir): ${100 - 100 * sourceStatistics.hashCalculated / sourceStatistics.files}%")
        }
        if (targetStatistics.files > 0) {
            println("Hash reused (targetDir): ${100 - 100 * targetStatistics.hashCalculated / targetStatistics.files}%")
        }

        val failures = mutableListOf<String>()
        var hasChanges = false

        with(FoldersContext(folders)) {
            with(caseSensitiveContext) {

                if (!checkAndFix(sourceChanges, targetChanges, syncResultFiles)) {
                    return
                }

                if (!furtherChecks(sourceDir, targetDir, sourceChanges, targetChanges, syncFilesParams)) {
                    return
                }

                val actionEnv = ActionEnv(syncResultFiles, failures, syncFilesParams.dryRun)

                createActions(sourceDir, targetDir, sourceChanges, targetChanges, changedDir, deletedDir)
                    .sortedWith(
                        compareBy(
                            { it.priority },
                            { foldersCtx.getFullPath(it.folderId).lowercase() },
                            { foldersCtx.getFullPath(it.folderId) },
                            { it.filename })
                    )
                    .forEach {
                        hasChanges = true
                        it.action(actionEnv)
                        testIfCancel()
                    }


                sourceChanges.deleted.filter { !it.isFolderMarker }.let {
                    if (it.isNotEmpty()) {
                        deletedFiles = equalsBy(HASH) { deletedFiles + it.map { it.copy(folderId = 0) } }.toList()
                    }
                }
                targetChanges.deleted.filter { !it.isFolderMarker }.let {
                    if (it.isNotEmpty()) {
                        deletedFiles = equalsBy(HASH) { deletedFiles + it.map { it.copy(folderId = 0) } }.toList()
                    }
                }
            }
        }


        fun backup(rootDir: File, file: File, suffix: String) {
            if (file.exists()) {
                Files.move(
                    file.toPath(),
                    File(rootDir, file.name.replace(suffix, "_old$suffix")).toPath(),
                    REPLACE_EXISTING
                )
            }
        }

        fun Collection<FileEntity>.usedFolderIds() = this.asSequence().filter { it.isFolderMarker }.map { it.folderId }.toSet()

        fun Collection<FileEntity>.saveIndexedFilesTo(file: File) {
            saveIndexedFiles(
                file,
                IndexedFilesEntity(
                    runDate = now.toKotlinLocalDateTime(),
                    files = this.toList(),
                    folder = folders.get(folders.rootFolderId).stripUnusedFolder(usedFolderIds()),
                )
            )
        }

        fun Collection<FileEntity>.saveSyncResultTo(file: File) {
            saveSyncResult(
                file,
                SyncResultEntity(
                    sourcePath = sourceDir.canonicalPath,
                    targetPath = targetDir.canonicalPath,
                    runDate = now.toKotlinLocalDateTime(),
                    failuresOccurred = failures,
                    files = this.toList(),
                    folder = folders.get(folders.rootFolderId).stripUnusedFolder(usedFolderIds()),
                )
            )
        }

        backup(sourceDir, indexedFilesFileSource, indexedFilesFileSuffix)
        backup(targetDir, indexedFilesFileTarget, indexedFilesFileSuffix)
        sourceChanges.allFilesBeforeSync.saveIndexedFilesTo(indexedFilesFileSource)
        targetChanges.allFilesBeforeSync.saveIndexedFilesTo(indexedFilesFileTarget)

        if (hasChanges) {

            if (!syncFilesParams.dryRun) {
                backup(sourceDir, syncResultFile, syncResultFileSuffix)

                syncResultFiles.saveSyncResultTo(syncResultFile)
            }

            if (!syncFilesParams.dryRun && deletedFiles.isNotEmpty()) {
                backup(sourceDir, deletedFilesFileSource, deletedFilesFileSuffix)
                backup(targetDir, deletedFilesFileTarget, deletedFilesFileSuffix)

                saveDeletedFiles(deletedFilesFileSource, DeletedFiles(deletedFiles))
                Files.copy(deletedFilesFileSource.toPath(), deletedFilesFileTarget.toPath(), COPY_ATTRIBUTES)
            }
        }
    }


    context(MutableFoldersContext)
    private fun FilesAndFolder.mapToRead(filter: Filter): List<FileEntity> {
        val mapping = mutableMapOf<Long, Long>()
        mapping[foldersCtx.rootFolderId] = foldersCtx.rootFolderId

        fun sync(folder: FolderEntity, parentFolderId: Long) {
            if (filter.folderFilter.excluded(foldersCtx.getFullPath(folder), folder.name) != null) {
                return
            }

            val folder1 = foldersCtx.getOrCreate(folder.name, parentFolderId)
            mapping[folder.id] = folder1.id
            folder.children.forEach {
                sync(it, folder1.id)
            }
        }
        this.folder.children.forEach {
            sync(it, foldersCtx.rootFolderId)
        }

        return this.files.mapNotNull { file ->
            mapping[file.folderId]?.let { folderId ->
                if (file.folderId != folderId) file.copy(folderId = folderId) else file
            }
        }
    }

}
