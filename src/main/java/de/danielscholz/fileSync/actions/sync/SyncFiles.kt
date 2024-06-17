package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.MutableFolders
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.persistence.*
import kotlinx.datetime.toKotlinLocalDateTime
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.LocalDateTime
import kotlin.collections.set


class SyncFiles(private val syncFilesParams: SyncFilesParams, private val sourceDir: File, private val targetDir: File, filter: Filter) {

    private val backupDir = ".syncFilesHistory"
    private val lockfileName = ".syncFiles_lockfile"
    private val indexedFilesFilePrefix = ".syncFilesIndex_"
    private val syncResultFilePrefix = ".syncFilesResult_"
    private val deletedFilesFilePrefix = ".deletedFiles"
    private val commonFileSuffix = ".jsn"

    private val now = LocalDateTime.now().ignoreMillis()
    private val dateTimeStr = now.toString().replace(":", "").replace("T", " ")
    private val changedDir = "$backupDir/modified/$dateTimeStr"
    private val deletedDir = "$backupDir/deleted/$dateTimeStr"

    private val syncName = syncFilesParams.syncName ?: (sourceDir.canonicalPath.toString() + "|" + targetDir.canonicalPath.toString()).hashCode().toString()

    private val indexedFilesFileSource = File(sourceDir, "$indexedFilesFilePrefix$syncName$commonFileSuffix")
    private val indexedFilesFileTarget = File(targetDir, "$indexedFilesFilePrefix$syncName$commonFileSuffix")

    private val syncResultFile = File(sourceDir, "$syncResultFilePrefix$syncName$commonFileSuffix")

    private val deletedFilesFileSource = File(sourceDir, "$deletedFilesFilePrefix$commonFileSuffix")
    private val deletedFilesFileTarget = File(targetDir, "$deletedFilesFilePrefix$commonFileSuffix")

    private val csSource = isCaseSensitiveFileSystem(sourceDir) ?: throw Exception("Unable to determine if filesystem $sourceDir is case sensitive!")
    private val csTarget = isCaseSensitiveFileSystem(targetDir) ?: throw Exception("Unable to determine if filesystem $targetDir is case sensitive!")

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


    fun sync() {
        guardWithLockFile(File(syncFilesParams.lockfileSourceDir ?: sourceDir, lockfileName)) {
            guardWithLockFile(File(syncFilesParams.lockfileTargetDir ?: targetDir, lockfileName)) {
                syncIntern()
            }
        }
    }

    private fun syncIntern() {
        println("Source dir: $sourceDir (case sensitive: $csSource)")
        println("Target dir: $targetDir (case sensitive: $csTarget)\n")

        val caseSensitiveContext = CaseSensitiveContext(csSource && csTarget)

        val lastSyncResult = readSyncResult(syncResultFile)

        val deletedFiles = mutableSetOf<DeletedFileEntity>()
        deletedFiles += readDeletedFiles(deletedFilesFileSource)?.files ?: setOf()
        deletedFiles += readDeletedFiles(deletedFilesFileTarget)?.files ?: setOf()

        val currentFilesSource: MutableCurrentFiles
        val currentFilesTarget: MutableCurrentFiles

        val sourceChanges: MutableChanges
        val targetChanges: MutableChanges

        val syncResultFiles: MutableSet<FileEntity>

        val sourceStatistics = MutableStatistics()
        val targetStatistics = MutableStatistics()

        val folders = MutableFolders()

        with(MutableFoldersContext(folders)) {

            val lastSyncResultFiles = lastSyncResult?.mapToRead(filter) ?: setOf()
            syncResultFiles = lastSyncResultFiles.toMutableSet()

            execute(
                {
                    with(CaseSensitiveContext(csSource)) {
                        val lastIndexedFilesSource = readIndexedFiles(indexedFilesFileSource)?.mapToRead(filter) ?: setOf()
                        with(MutableStatisticsContext(sourceStatistics)) {
                            currentFilesSource = getCurrentFiles(sourceDir, filter, lastIndexedFilesSource)
                        }
                        backup(sourceDir, indexedFilesFileSource)
                        currentFilesSource.files.saveIndexedFilesTo(indexedFilesFileSource) // already save indexed files in the event of a subsequent error
                        sourceChanges = getChanges(sourceDir, lastSyncResultFiles, currentFilesSource)
                    }
                },
                {
                    with(CaseSensitiveContext(csTarget)) {
                        val lastIndexedFilesTarget = readIndexedFiles(indexedFilesFileTarget)?.mapToRead(filter) ?: setOf()
                        with(MutableStatisticsContext(targetStatistics)) {
                            currentFilesTarget = getCurrentFiles(targetDir, filter, lastIndexedFilesTarget)
                        }
                        backup(targetDir, indexedFilesFileTarget)
                        currentFilesTarget.files.saveIndexedFilesTo(indexedFilesFileTarget) // already save indexed files in the event of a subsequent error
                        targetChanges = getChanges(targetDir, lastSyncResultFiles, currentFilesTarget)
                    }
                },
                parallel = syncFilesParams.parallelIndexing
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

        currentFilesSource.folderRenamed.forEach {
            println("Folder renamed (source): ${folders.get(it.key)} -> ${folders.get(it.value)}")
        }
        currentFilesTarget.folderRenamed.forEach {
            println("Folder renamed (target): ${folders.get(it.key)} -> ${folders.get(it.value)}")
        }

        val failures = mutableListOf<String>()
        val hasChanges: Boolean

        with(FoldersContext(folders)) {
            with(caseSensitiveContext) {

                val duplicateFilesSource = getDuplicateFiles(currentFilesSource)
                val duplicateFilesTarget = getDuplicateFiles(currentFilesTarget)

                println("Duplicates (source): ${duplicateFilesSource.let { it.sumOfDuplFileSizes.formatAsFileSize() + " (${it.totalDuplFiles} files, ${(it.sumOfDuplFileSizes - it.nettoSpaceNeeded).formatAsFileSize()} could be released)" }}")
                duplicateFilesSource.foldersWithDuplFiles.take(10).forEach {
                    println(sourceDir.toString() + it.first + " " + it.second.formatAsFileSize())
                }

                println("Duplicates (target): ${duplicateFilesTarget.let { it.sumOfDuplFileSizes.formatAsFileSize() + " (${it.totalDuplFiles} files, ${(it.sumOfDuplFileSizes - it.nettoSpaceNeeded).formatAsFileSize()} could be released)" }}")
                duplicateFilesTarget.foldersWithDuplFiles.take(10).forEach {
                    println(targetDir.toString() + it.first + " " + it.second.formatAsFileSize())
                }

                if (!checkAndFix(sourceChanges, targetChanges, currentFilesSource, currentFilesTarget, syncResultFiles)) {
                    return
                }

                if (!furtherChecks(sourceDir, targetDir, sourceChanges, targetChanges, currentFilesSource, currentFilesTarget, syncFilesParams)) {
                    return
                }

                val actions = createActions(sourceChanges, targetChanges)
                    .sortedWith(
                        compareBy(
                            { it.locationOfChangesToBeMade }, // first: all changes within sourceDir
                            { it.priority },
                            { foldersCtx.getFullPath(it.folderId).lowercase() },
                            { foldersCtx.getFullPath(it.folderId) }, // needed to get a stable sort
                            { it.filename.lowercase() },
                            { it.filename }, // needed to get a stable sort
                        )
                    )

                val actionEnv = ActionEnv(
                    sourceDir = sourceDir,
                    targetDir = targetDir,
                    changedDir = changedDir,
                    deletedDir = deletedDir,
                    syncResultFiles = syncResultFiles,
                    currentFilesTarget = currentFilesTarget.files,
                    failures = failures,
                    dryRun = syncFilesParams.dryRun
                )

                val actionEnvReversed = ActionEnv(
                    sourceDir = targetDir,
                    targetDir = sourceDir,
                    changedDir = changedDir,
                    deletedDir = deletedDir,
                    syncResultFiles = syncResultFiles,
                    currentFilesTarget = currentFilesSource.files,
                    failures = failures,
                    dryRun = syncFilesParams.dryRun
                )

                actions.forEach {
                    it.action(if (!it.switchedSourceAndTarget) actionEnv else actionEnvReversed)
                    testIfCancel()
                }

                hasChanges = actions.isNotEmpty()
            }
        }

        (sourceChanges.deleted + targetChanges.deleted).filter { !it.isFolderMarker }.let { list ->
            deletedFiles += list.map { DeletedFileEntity(it.hash?.hash, it.name) }
        }

        if ((hasChanges || !syncResultFile.exists()) && !syncFilesParams.dryRun) {
            backup(sourceDir, syncResultFile)

            with(FoldersContext(folders)) {

                syncResultFiles.saveSyncResultTo(syncResultFile, failures)

                if (hasChanges) {
                    currentFilesSource.files.saveIndexedFilesTo(indexedFilesFileSource) // save again; it may have changed
                    currentFilesTarget.files.saveIndexedFilesTo(indexedFilesFileTarget)
                }
            }


            if (deletedFiles.isNotEmpty()) {
                backup(sourceDir, deletedFilesFileSource)
                backup(targetDir, deletedFilesFileTarget)

                saveDeletedFiles(deletedFilesFileSource, DeletedFilesEntity(deletedFiles))
                Files.copy(deletedFilesFileSource.toPath(), deletedFilesFileTarget.toPath(), COPY_ATTRIBUTES)
            }
        }
    }

    class DuplFilesResult(
        val totalDuplFiles: Int,
        val sumOfDuplFileSizes: Long,
        val nettoSpaceNeeded: Long,
        val foldersWithDuplFiles: List<Pair<String, Long>>
    )

    context(FoldersContext)
    private fun getDuplicateFiles(currentFilesSource: MutableCurrentFiles): DuplFilesResult {
        val map = mutableListMultimapOf<String, FileEntity>()
        currentFilesSource.files.forEach { file ->
            file.hash?.hash?.let {
                map.put(it, file)
            }
        }
        val list = map.asMap().values.filter { it.size > 1 }

        val foldersWithDuplFiles = list
            .flatMap { duplFiles -> duplFiles.map { foldersCtx.getFullPath(it.folderId) to it.size } }
            .groupingBy { it.first }
            .fold(0L) { m, c -> m + c.second }
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        return DuplFilesResult(list.sumOf { it.size }, list.sumOf { it.sumOf { it.size } }, list.sumOf { it.first().size }, foldersWithDuplFiles)
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
    private fun Set<FileEntity>.saveIndexedFilesTo(file: File) {
        saveIndexedFiles(
            file,
            IndexedFilesEntity(
                runDate = now.toKotlinLocalDateTime(),
                files = this,
                rootFolder = foldersCtx.get(foldersCtx.rootFolderId).stripUnusedFolder(this.usedFolderIds()),
            )
        )
    }

    context(FoldersContext)
    private fun Set<FileEntity>.saveSyncResultTo(file: File, failures: List<String>) {
        saveSyncResult(
            file,
            SyncResultEntity(
                sourcePath = sourceDir.canonicalPath,
                targetPath = targetDir.canonicalPath,
                runDate = now.toKotlinLocalDateTime(),
                failuresOccurred = failures,
                files = this,
                rootFolder = foldersCtx.get(foldersCtx.rootFolderId).stripUnusedFolder(this.usedFolderIds()),
            )
        )
    }

    context(MutableFoldersContext)
    private fun FilesAndFolder.mapToRead(filter: Filter): Set<FileEntity> {
        val mapping = mutableMapOf<Long, Long>()
        mapping[foldersCtx.rootFolderId] = foldersCtx.rootFolderId

        fun sync(folder: FolderEntity, parentFolderId: Long) {
            if (filter.folderFilter.excluded(foldersCtx.getFullPath(parentFolderId) + folder.name + "/", folder.name) != null) {
                return
            }

            val folderMapped = foldersCtx.getOrCreate(folder.name, parentFolderId)
            mapping[folder.id] = folderMapped.id
            folder.children.forEach { childFolder ->
                sync(childFolder, folderMapped.id)
            }
        }

        foldersCtx.check()

        this.rootFolder.children.forEach { childFolder ->
            sync(childFolder, foldersCtx.rootFolderId)
        }

        foldersCtx.check()

        return this.files.mapNotNull { file ->
            mapping[file.folderId]?.let { folderId ->
                if (file.folderId != folderId) file.copy(folderId = folderId) else file
            }
        }.toSet()
    }

}
