package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.MutableFolders
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.MatchMode
import de.danielscholz.fileSync.matching.equalsForFileBy
import de.danielscholz.fileSync.matching.pathAndName
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
class SyncFiles(
    private val syncFilesParams: SyncFilesParams,
    sourceDir: File,
    targetDir: File,
    filter: Filter,
    encryptSourcePaths: List<PathMatcher>,
    encryptTargetPaths: List<PathMatcher>
) {

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

    class Env(
        val name: String,
        val dir: File,
        val indexedFilesFile: File,
        val deletedFilesFile: File,
        val caseSensitive: Boolean,
        val uiDir: UI.Dir,
        val encryptPaths: List<PathMatcher>,
        val password: String?
    ) {
        val statistics = MutableStatistics(uiDir)
    }

    private val source = Env(
        "source",
        sourceDir,
        File(sourceDir, "$indexedFilesFilePrefix$syncName$commonFileSuffix"),
        File(sourceDir, "$deletedFilesFilePrefix$commonFileSuffix"),
        isCaseSensitiveFileSystem(sourceDir) ?: throw Exception("Unable to determine if filesystem $sourceDir is case sensitive!"),
        UI.sourceDir,
        encryptSourcePaths,
        syncFilesParams.passwordSource
    )

    private val target = Env(
        "target",
        targetDir,
        File(targetDir, "$indexedFilesFilePrefix$syncName$commonFileSuffix"),
        File(targetDir, "$deletedFilesFilePrefix$commonFileSuffix"),
        isCaseSensitiveFileSystem(targetDir) ?: throw Exception("Unable to determine if filesystem $targetDir is case sensitive!"),
        UI.targetDir,
        encryptTargetPaths,
        syncFilesParams.passwordTarget
    )


    private val syncResultFile = File(source.dir, "$syncResultFilePrefix$syncName$commonFileSuffix")


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
        guardWithLockFile(File(syncFilesParams.lockfileSourceDir ?: source.dir, lockfileName)) {
            guardWithLockFile(File(syncFilesParams.lockfileTargetDir ?: target.dir, lockfileName)) {

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
        println("Source dir: ${source.dir} (case sensitive: ${source.caseSensitive})")
        println("Target dir: ${target.dir} (case sensitive: ${target.caseSensitive})\n")

        val caseSensitiveContext = CaseSensitiveContext(source.caseSensitive && target.caseSensitive)

        val currentFilesSource: MutableCurrentFiles
        val currentFilesTarget: MutableCurrentFiles

        val sourceChanges: MutableChanges
        val targetChanges: MutableChanges

        val syncResultFiles: MutableSet<FileEntity>

        val fs = FileSystemEncryption(source, target, changedDir, deletedDir, syncFilesParams.dryRun)

        val folders = MutableFolders()

        with(MutableFoldersContext(folders)) {

            val lastSyncResultFiles = readSyncResult(syncResultFile)?.mapToRead(filter) ?: setOf()
            syncResultFiles = lastSyncResultFiles.toMutableSet()

            fun CaseSensitiveContext.getCurrentFiles(env: Env): MutableCurrentFiles {
                val currentFiles: MutableCurrentFiles
                val lastIndexedFiles = readIndexedFiles(env.indexedFilesFile)
                val lastIndexedFilesMapped = lastIndexedFiles?.mapToRead(filter) ?: mutableSetOf() // TODO filter files?!

                if (syncFilesParams.skipIndexing) {
                    return MutableCurrentFiles(lastIndexedFilesMapped)
                }

                with(MutableStatisticsContext(env.statistics)) {
                    currentFiles = getCurrentFiles(
                        env.dir,
                        filter,
                        lastIndexedFilesMapped,
                        lastIndexedFiles?.runDate ?: PAST_LOCAL_DATE_TIME,
                        syncName,
                        syncFilesParams.considerOtherIndexedFilesWithSyncName,
                        { env.uiDir.currentReadDir = it },
                        now,
                        fs
                    )
                }
                env.uiDir.currentReadDir = null
                backup(env.dir, env.indexedFilesFile)
                currentFiles.files.saveIndexedFilesTo(env.indexedFilesFile, now) // already save indexed files in the event of a subsequent error
                return currentFiles
            }

            execute(
                {
                    with(CaseSensitiveContext(source.caseSensitive)) {
                        currentFilesSource = getCurrentFiles(source)
                        sourceChanges = getChanges(source.dir, lastSyncResultFiles, currentFilesSource)
                    }
                },
                {
                    with(CaseSensitiveContext(target.caseSensitive)) {
                        currentFilesTarget = getCurrentFiles(target)
                        targetChanges = getChanges(target.dir, lastSyncResultFiles, currentFilesTarget)
                    }
                },
                parallel = syncFilesParams.parallelIndexing
            )
        }

        if (syncFilesParams.backupMode && targetChanges.hasChanges()) {
            throw Exception("Target directory has changes, which is not allowed in backupMode!")
        }

        println("Files / Folders (source dir): ${source.statistics.filesCount} / ${source.statistics.foldersCount}")
        println("Files / Folders (target dir): ${target.statistics.filesCount} / ${target.statistics.foldersCount}")
        if (source.statistics.filesCount > 0) {
            println("Hash reused (source dir): ${100 - 100 * source.statistics.filesHashCalculatedCount / source.statistics.filesCount}%")
        }
        if (target.statistics.filesCount > 0) {
            println("Hash reused (target dir): ${100 - 100 * target.statistics.filesHashCalculatedCount / target.statistics.filesCount}%")
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

        with(FoldersContext(folders)) {
            with(caseSensitiveContext) {

                printoutDuplFiles(currentFilesSource.files, "source")
                printoutDuplFiles(currentFilesTarget.files, "target")

                if (!checkAndFix(source.dir, target.dir, sourceChanges, targetChanges, currentFilesSource, currentFilesTarget, syncResultFiles, fs)) {
                    if (!syncFilesParams.ignoreConflicts) return
                }

                UI.totalBytesToCopy = sourceChanges.diskspaceNeeded() + targetChanges.diskspaceNeeded()

                equalsForFileBy(pathAndName) {
                    (currentFilesSource.files.filter { it.isEmptyFile } + currentFilesTarget.files.filter { it.isEmptyFile })
                        .filter { it.name != ".gitkeep" }
                        .forEach {
                            addWarning("File is empty: ${it.pathAndName()}")
                        }
                }

                if (!furtherChecks(source.dir, target.dir, sourceChanges, targetChanges, currentFilesSource, currentFilesTarget, syncFilesParams)) {
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
                    sourceDir = source.dir,
                    targetDir = target.dir,
                    changedDir = changedDir,
                    deletedDir = deletedDir,
                    syncResultFiles = syncResultFiles,
                    currentFilesTarget = currentFilesTarget.files,
                    addFailure = ::addFailure,
                    fs = fs
                )

                val actionEnvReversed = ActionEnv(
                    sourceDir = target.dir,
                    targetDir = source.dir,
                    changedDir = changedDir,
                    deletedDir = deletedDir,
                    syncResultFiles = syncResultFiles,
                    currentFilesTarget = currentFilesSource.files,
                    addFailure = ::addFailure,
                    fs = fs
                )

                try {
                    UI.totalActions = actions.size

                    actions.forEach {
                        it.action(if (!it.switchedSourceAndTarget) actionEnv else actionEnvReversed)
                        UI.actionsExecuted++
                        testIfCancel()
                        if (syncFilesParams.dryRun) Thread.sleep(20)
                    }

                    UI.clearCurrentOperations()

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
                                    val s = if (possibleMatches.isNotEmpty()) {
                                        " But there are possible matches: ${possibleMatches.joinToString { "${it.pathAndName()} (modified: ${it.modified.toStr()})" }}"
                                    } else ""
                                    val msg =
                                        "${it.pathAndName()} (modified: ${it.modified.toStr()}) This file within 'copy' directory is NOT a copy. Original file could have been deleted or modified!$s"
                                    println(msg)
                                    addWarning(msg)
                                }
                        }
                    }

                } finally {

                    val deletedFiles = mutableSetOf<DeletedFileEntity>()
                    exec {
                        val hashes: Set<String> by myLazy { syncResultFiles.mapNotNullTo(mutableSetOf()) { it.hash } }
                        (sourceChanges.deleted + targetChanges.deleted).filter { !it.isFolderMarker && (it.hash == null || it.hash !in hashes) }.let { list ->
                            deletedFiles += readDeletedFiles(source.deletedFilesFile)?.files ?: setOf()
                            deletedFiles += readDeletedFiles(target.deletedFilesFile)?.files ?: setOf()
                            deletedFiles += list.map { DeletedFileEntity(it.hash, it.name) }
                        }
                    }

                    val hasChanges = actionEnv.successfullyRealProcessed > 0 || actionEnvReversed.successfullyRealProcessed > 0

                    if ((hasChanges || !syncResultFile.exists()) && !syncFilesParams.dryRun) {
                        backup(source.dir, syncResultFile)

                        syncResultFiles.saveSyncResultTo(syncResultFile, failures)

                        if (hasChanges) {
                            currentFilesSource.files.saveIndexedFilesTo(source.indexedFilesFile, now) // save again; at least one of this files
                            currentFilesTarget.files.saveIndexedFilesTo(target.indexedFilesFile, now) // should have changed
                        }

                        if (deletedFiles.isNotEmpty()) {
                            backup(source.dir, source.deletedFilesFile)
                            backup(target.dir, target.deletedFilesFile)

                            saveDeletedFiles(source.deletedFilesFile, DeletedFilesEntity(deletedFiles))
                            Files.copy(source.deletedFilesFile.toPath(), target.deletedFilesFile.toPath(), COPY_ATTRIBUTES)
                        }
                    }
                }
            }
        }
    }

    context(FoldersContext)
    private fun printoutDuplFiles(files: Set<FileEntity>, name: String) {
        val duplicateFiles = getDuplicateFiles(files)
        if (duplicateFiles.totalSpace > 0) {
            println("Duplicates ($name): ${duplicateFiles.let { (it.totalSpace - it.nettoSpaceNeeded).formatAsFileSize() + " (${it.totalDuplFiles - it.nettoFiles} files)" }}")
            duplicateFiles.foldersWithDuplFiles.take(10).forEach { (dir, duplFileSize) ->
                println("${source.dir}$dir ${duplFileSize.formatAsFileSize()}")
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
                sourcePath = source.dir.canonicalPath,
                targetPath = target.dir.canonicalPath,
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
