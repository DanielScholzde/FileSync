package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.MutableFolders
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.MatchMode
import de.danielscholz.fileSync.matching.equalsForFileBy
import de.danielscholz.fileSync.persistence.*
import de.danielscholz.fileSync.ui.UI
import de.danielscholz.fileSync.ui.startUiBlocking
import kotlinx.datetime.toKotlinLocalDateTime
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.concurrent.thread
import java.time.LocalDateTime as JavaLocalDateTime


@Suppress("ConstPropertyName")
class SyncFiles(private val syncFilesParams: SyncFilesParams, private val sourceDir: File, private val targetDir: File, filter: Filter) {

    companion object {
        private const val backupDir = ".syncFilesHistory"
        private const val lockfileName = ".syncFiles_lockfile"
        internal const val indexedFilesFilePrefix = ".syncFilesIndex_"
        private const val syncResultFilePrefix = ".syncFilesResult_"
        private const val deletedFilesFilePrefix = ".deletedFiles"
        internal const val commonFileSuffix = ".jsn"
    }

    private val now = JavaLocalDateTime.now().ignoreMillis().toKotlinLocalDateTime()
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

                thread {
                    startUiBlocking()
                }

                try {
                    syncIntern()
                } finally {
                    UI.syncFinished = true
                }
            }
        }
    }

    private fun syncIntern() {
        println("Source dir: $sourceDir (case sensitive: $csSource)")
        println("Target dir: $targetDir (case sensitive: $csTarget)\n")

        val caseSensitiveContext = CaseSensitiveContext(csSource && csTarget)

        val currentFilesSource: MutableCurrentFiles
        val currentFilesTarget: MutableCurrentFiles

        val sourceChanges: MutableChanges
        val targetChanges: MutableChanges

        val syncResultFiles: MutableSet<FileEntity>

        val sourceStatistics = MutableStatistics(UI.sourceDir)
        val targetStatistics = MutableStatistics(UI.targetDir)

        val folders = MutableFolders()

        with(MutableFoldersContext(folders)) {

            val lastSyncResultFiles = readSyncResult(syncResultFile)?.mapToRead(filter) ?: setOf()
            syncResultFiles = lastSyncResultFiles.toMutableSet()

            execute(
                {
                    with(CaseSensitiveContext(csSource)) {
                        val lastIndexedFiles = readIndexedFiles(indexedFilesFileSource)
                        val lastIndexedFilesMapped = lastIndexedFiles?.mapToRead(filter) ?: setOf()
                        with(MutableStatisticsContext(sourceStatistics)) {
                            currentFilesSource = getCurrentFiles(
                                sourceDir,
                                filter,
                                lastIndexedFilesMapped,
                                lastIndexedFiles?.runDate ?: PAST_LOCAL_DATE_TIME,
                                syncName,
                                syncFilesParams.considerOtherIndexedFilesWithSyncName,
                                { UI.sourceDir.currentReadDir = it },
                                now
                            )
                        }
                        UI.sourceDir.currentReadDir = null
                        backup(sourceDir, indexedFilesFileSource)
                        currentFilesSource.files.saveIndexedFilesTo(indexedFilesFileSource, now) // already save indexed files in the event of a subsequent error
                        sourceChanges = getChanges(sourceDir, lastSyncResultFiles, currentFilesSource)
                    }
                },
                {
                    with(CaseSensitiveContext(csTarget)) {
                        val lastIndexedFiles = readIndexedFiles(indexedFilesFileTarget)
                        val lastIndexedFilesMapped = lastIndexedFiles?.mapToRead(filter) ?: setOf()
                        with(MutableStatisticsContext(targetStatistics)) {
                            currentFilesTarget = getCurrentFiles(
                                targetDir,
                                filter,
                                lastIndexedFilesMapped,
                                lastIndexedFiles?.runDate ?: PAST_LOCAL_DATE_TIME,
                                syncName,
                                syncFilesParams.considerOtherIndexedFilesWithSyncName,
                                { UI.targetDir.currentReadDir = it },
                                now
                            )
                        }
                        UI.targetDir.currentReadDir = null
                        backup(targetDir, indexedFilesFileTarget)
                        currentFilesTarget.files.saveIndexedFilesTo(indexedFilesFileTarget, now) // already save indexed files in the event of a subsequent error
                        targetChanges = getChanges(targetDir, lastSyncResultFiles, currentFilesTarget)
                    }
                },
                parallel = syncFilesParams.parallelIndexing
            )
        }

        if (syncFilesParams.backupMode && targetChanges.hasChanges()) {
            throw Exception("Target directory has changes, which is not allowed in backupMode!")
        }

        println("Files / Folders (sourceDir): ${sourceStatistics.filesCount} / ${sourceStatistics.foldersCount}")
        println("Files / Folders (targetDir): ${targetStatistics.filesCount} / ${targetStatistics.foldersCount}")
        if (sourceStatistics.filesCount > 0) {
            println("Hash reused (sourceDir): ${100 - 100 * sourceStatistics.filesHashCalculatedCount / sourceStatistics.filesCount}%")
        }
        if (targetStatistics.filesCount > 0) {
            println("Hash reused (targetDir): ${100 - 100 * targetStatistics.filesHashCalculatedCount / targetStatistics.filesCount}%")
        }

        val failures = mutableListOf<String>()

        fun addFailure(failure: String) {
            failures += failure
            UI.failures = failures.toList() // create immutable copy
        }

        val warnings = mutableListOf<String>()

        fun addWarning(warning: String) {
            warnings += warning
            UI.warnings = warnings.toList() // create immutable copy
        }

        val hasChanges: Boolean

        with(FoldersContext(folders)) {
            with(caseSensitiveContext) {

                printoutDuplFiles(currentFilesSource.files, "source")
                printoutDuplFiles(currentFilesTarget.files, "target")

                if (!checkAndFix(sourceDir, targetDir, sourceChanges, targetChanges, currentFilesSource, currentFilesTarget, syncResultFiles)) {
                    if (!syncFilesParams.ignoreConflicts) return
                }

                UI.totalBytesToCopy = sourceChanges.diskspaceNeeded() + targetChanges.diskspaceNeeded()

                if (!furtherChecks(sourceDir, targetDir, sourceChanges, targetChanges, currentFilesSource, currentFilesTarget, syncFilesParams)) {
                    return
                }

                // to be sure, repeat check:
                if (syncFilesParams.backupMode && targetChanges.hasChanges()) {
                    throw Exception("Target directory has changes, which is not allowed in backupMode!")
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
                    addFailure = ::addFailure,
                    dryRun = syncFilesParams.dryRun
                )

                val actionEnvReversed = ActionEnv(
                    sourceDir = targetDir,
                    targetDir = sourceDir,
                    changedDir = changedDir,
                    deletedDir = deletedDir,
                    syncResultFiles = syncResultFiles,
                    currentFilesTarget = currentFilesSource.files,
                    addFailure = ::addFailure,
                    dryRun = syncFilesParams.dryRun
                )

                actions.forEach {
                    it.action(if (!it.switchedSourceAndTarget) actionEnv else actionEnvReversed)
                    testIfCancel()
                }

                UI.clearCurrentOperations()

                hasChanges = actions.isNotEmpty()

                printoutDuplFiles(syncResultFiles, "sync result")

                if (syncFilesParams.warnIfFileCopyHasNoOriginal) {
                    equalsForFileBy(MatchMode.HASH) {
                        val fileCopies = syncResultFiles.filter { it.isWithinDirCopy() && !it.isFolderMarker }
                        val notFileCopies = syncResultFiles.filter { !it.isWithinDirCopy() }
                        val notFileCopiesByName = notFileCopies.multiAssociateBy { it.name }
                        (fileCopies subtract notFileCopies)
                            .sortedBy { it.pathAndName() }
                            .forEach {
                                val possibleMatches = notFileCopiesByName[it.name]
                                val s =
                                    if (possibleMatches.isNotEmpty()) " But there are possible matches: ${possibleMatches.joinToString { "${it.pathAndName()} (modified: ${it.modified.toStr()})" }}" else ""
                                val msg =
                                    "${it.pathAndName()} (modified: ${it.modified.toStr()}) This file within 'copy' directory is NOT a copy. Original file could have been deleted or modified!$s"
                                println(msg)
                                addWarning(msg)
                            }
                    }
                }
            }
        }


        val deletedFiles = mutableSetOf<DeletedFileEntity>()
        exec {
            val hashes: Set<String> by myLazy { syncResultFiles.mapNotNullTo(mutableSetOf()) { it.hash } }
            (sourceChanges.deleted + targetChanges.deleted).filter { !it.isFolderMarker && (it.hash == null || it.hash !in hashes) }.let { list ->
                deletedFiles += readDeletedFiles(deletedFilesFileSource)?.files ?: setOf()
                deletedFiles += readDeletedFiles(deletedFilesFileTarget)?.files ?: setOf()
                deletedFiles += list.map { DeletedFileEntity(it.hash, it.name) }
            }
        }

        if ((hasChanges || !syncResultFile.exists()) && !syncFilesParams.dryRun) {
            backup(sourceDir, syncResultFile)

            with(FoldersContext(folders)) {

                syncResultFiles.saveSyncResultTo(syncResultFile, failures)

                if (hasChanges) {
                    currentFilesSource.files.saveIndexedFilesTo(indexedFilesFileSource, now) // save again; it may have changed
                    currentFilesTarget.files.saveIndexedFilesTo(indexedFilesFileTarget, now)
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

    context(FoldersContext)
    private fun printoutDuplFiles(files: Set<FileEntity>, name: String) {
        val duplicateFiles = getDuplicateFiles(files)
        if (duplicateFiles.totalSpace > 0) {
            println("Duplicates ($name): ${duplicateFiles.let { (it.totalSpace - it.nettoSpaceNeeded).formatAsFileSize() + " (${it.totalDuplFiles - it.nettoFiles} files)" }}")
            duplicateFiles.foldersWithDuplFiles.take(10).forEach { (dir, duplFileSize) ->
                println("$sourceDir$dir ${duplFileSize.formatAsFileSize()}")
            }
        }
    }

    class DuplFilesResult(
        val totalDuplFiles: Int,
        val nettoFiles: Int,
        val totalSpace: Long,
        val nettoSpaceNeeded: Long,
        val foldersWithDuplFiles: List<Pair<String, Long>>
    )

    context(FoldersContext)
    private fun getDuplicateFiles(files: Set<FileEntity>): DuplFilesResult {
        val map = mutableListMultimapOf<String, FileEntity>()
        files.filter { !it.isWithinDirCopy() }
            .forEach { file ->
                file.hash?.let {
                    map.put(it, file)
                }
            }
        val list: List<Collection<FileEntity>> = map.asMap().values.filter { it.size > 1 }

        val foldersWithDuplFiles = list
            .flatMap { duplFiles -> duplFiles.map { foldersCtx.getFullPath(it.folderId) to it.size } }
            .groupingBy { it.first }
            .fold(0L) { accValue, elem -> accValue + elem.second }
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        return DuplFilesResult(
            totalDuplFiles = list.sumOf { it.size },
            nettoFiles = list.size,
            totalSpace = list.sumOf { it.sumOf { it.size } },
            nettoSpaceNeeded = list.sumOf { it.first().size },
            foldersWithDuplFiles = foldersWithDuplFiles
        )
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
    private fun Set<FileEntity>.saveSyncResultTo(file: File, failures: List<String>) {
        saveSyncResult(
            file,
            SyncResultEntity(
                sourcePath = sourceDir.canonicalPath,
                targetPath = targetDir.canonicalPath,
                runDate = now,
                failuresOccurred = failures,
                files = this,
                rootFolder = foldersCtx.get(foldersCtx.rootFolderId).stripUnusedFolder(this.usedFolderIds()),
            )
        )
    }

    context(FoldersContext)
    private fun FileEntity.isWithinDirCopy(): Boolean {
        val path = this.path().lowercase()
        return " kopie/" in path ||
                "_kopie/" in path ||
                " copy/" in path ||
                "_copy/" in path
    }
}
