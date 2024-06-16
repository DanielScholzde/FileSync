package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.FoldersImpl
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.MatchMode.HASH
import de.danielscholz.fileSync.matching.equalsBy
import de.danielscholz.fileSync.persistence.*
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
    private val syncResultFilePrefix = ".syncFilesResult_"
    private val deletedFilesFilePrefix = ".deletedFiles"
    private val commonFileSuffix = ".jsn"

    fun sync(sourceDir: File, targetDir: File, filter: Filter) {
        guardWithLockFile(File(syncFilesParams.lockfileSourceDir ?: sourceDir, lockfileName)) {
            guardWithLockFile(File(syncFilesParams.lockfileTargetDir ?: targetDir, lockfileName)) {
                syncIntern(sourceDir, targetDir, filter)
            }
        }
    }

    private val now = LocalDateTime.now().ignoreMillis()
    private val dateTimeStr = now.toString().replace(":", "").replace("T", " ")
    private val changedDir = "$backupDir/modified/$dateTimeStr"
    private val deletedDir = "$backupDir/deleted/$dateTimeStr"

    private fun syncIntern(sourceDir: File, targetDir: File, filter: Filter) {
        println("Source dir: $sourceDir")
        println("Target dir: $targetDir\n")

        val caseSensitiveContext = CaseSensitiveContext(
            (isCaseSensitiveFileSystem(sourceDir) ?: throw Exception("Unable to determine if filesystem $sourceDir is case sensitive!")) &&
                    (isCaseSensitiveFileSystem(targetDir) ?: throw Exception("Unable to determine if filesystem $targetDir is case sensitive!"))
        )

        val syncName = syncFilesParams.syncName ?: (sourceDir.canonicalPath.toString() + "|" + targetDir.canonicalPath.toString()).hashCode().toString()

        val indexedFilesFileSource = File(sourceDir, "$indexedFilesFilePrefix$syncName$commonFileSuffix")
        val indexedFilesFileTarget = File(targetDir, "$indexedFilesFilePrefix$syncName$commonFileSuffix")
        val syncResultFile = File(sourceDir, "$syncResultFilePrefix$syncName$commonFileSuffix")
        val deletedFilesFileSource = File(sourceDir, "$deletedFilesFilePrefix$commonFileSuffix")
        val deletedFilesFileTarget = File(targetDir, "$deletedFilesFilePrefix$commonFileSuffix")

        val lastSyncResult = readSyncResult(syncResultFile)

        var deletedFiles = let {
            val deletedFilesSource = readDeletedFiles(deletedFilesFileSource)
            val deletedFilesTarget = readDeletedFiles(deletedFilesFileTarget)

            equalsBy(HASH) {
                (deletedFilesSource?.files ?: listOf()) + (deletedFilesTarget?.files ?: listOf())
            }
        }

        @Suppress("NAME_SHADOWING")
        val filter = Filter(
            fileFilter = { path, fileName ->
                if (fileName.startsWith(indexedFilesFilePrefix) && fileName.endsWith(commonFileSuffix) ||
                    fileName.startsWith(syncResultFilePrefix) && fileName.endsWith(commonFileSuffix) ||
                    fileName.startsWith(deletedFilesFilePrefix) && fileName.endsWith(commonFileSuffix) ||
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

            val lastSyncResultFiles = lastSyncResult?.mapToRead(filter) ?: listOf()
            syncResultFiles = lastSyncResultFiles.toMutableSet()
            if (lastSyncResultFiles.size != syncResultFiles.size) throw Exception()

            parallel(
                {
                    val lastIndexedFilesSourceFiles = readIndexedFiles(indexedFilesFileSource)?.mapToRead(filter) ?: listOf()
                    val currentFilesSource = getCurrentFiles(sourceDir, filter, lastIndexedFilesSourceFiles, sourceStatistics)
                    sourceChanges = getChanges(sourceDir, lastSyncResultFiles, currentFilesSource)
                    backup(sourceDir, indexedFilesFileSource)
                    sourceChanges.allFilesBeforeSync.saveIndexedFilesTo(indexedFilesFileSource)
                },
                {
                    val lastIndexedFilesTargetFiles = readIndexedFiles(indexedFilesFileTarget)?.mapToRead(filter) ?: listOf()
                    val currentFilesTarget = getCurrentFiles(targetDir, filter, lastIndexedFilesTargetFiles, targetStatistics)
                    targetChanges = getChanges(targetDir, lastSyncResultFiles, currentFilesTarget)
                    backup(targetDir, indexedFilesFileTarget)
                    targetChanges.allFilesBeforeSync.saveIndexedFilesTo(indexedFilesFileTarget)
                },
                syncFilesParams.parallelIndexing
            )
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
                        deletedFiles = equalsBy(HASH) { deletedFiles + it.map { it.copy(folderId = 0) } }
                    }
                }
                targetChanges.deleted.filter { !it.isFolderMarker }.let {
                    if (it.isNotEmpty()) {
                        deletedFiles = equalsBy(HASH) { deletedFiles + it.map { it.copy(folderId = 0) } }
                    }
                }
            }
        }

        hasChanges = hasChanges || !syncResultFile.exists() // if syncResultFile does not exist, set hasChanges to true

        if (hasChanges && !syncFilesParams.dryRun) {
            backup(sourceDir, syncResultFile)

            with(FoldersContext(folders)) {
                syncResultFiles.saveSyncResultTo(syncResultFile, sourceDir, targetDir, failures)
            }

            if (deletedFiles.isNotEmpty()) {
                backup(sourceDir, deletedFilesFileSource)
                backup(targetDir, deletedFilesFileTarget)

                saveDeletedFiles(deletedFilesFileSource, DeletedFiles(deletedFiles))
                Files.copy(deletedFilesFileSource.toPath(), deletedFilesFileTarget.toPath(), COPY_ATTRIBUTES)
            }
        }
    }

    private fun backup(rootDir: File, file: File) {
        if (file.exists()) {
            Files.move(
                file.toPath(),
                File(rootDir, file.name.replace(commonFileSuffix, "_old$commonFileSuffix")).toPath(),
                REPLACE_EXISTING
            )
        }
    }

    context(FoldersContext)
    private fun Collection<FileEntity>.saveIndexedFilesTo(file: File) {
        saveIndexedFiles(
            file,
            IndexedFilesEntity(
                runDate = now.toKotlinLocalDateTime(),
                files = this.toList(),
                folder = foldersCtx.get(foldersCtx.rootFolderId).stripUnusedFolder(this.usedFolderIds()),
            )
        )
    }

    context(FoldersContext)
    private fun Collection<FileEntity>.saveSyncResultTo(file: File, sourceDir: File, targetDir: File, failures: List<String>) {
        saveSyncResult(
            file,
            SyncResultEntity(
                sourcePath = sourceDir.canonicalPath,
                targetPath = targetDir.canonicalPath,
                runDate = now.toKotlinLocalDateTime(),
                failuresOccurred = failures,
                files = this.toList(),
                folder = foldersCtx.get(foldersCtx.rootFolderId).stripUnusedFolder(this.usedFolderIds()),
            )
        )
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
