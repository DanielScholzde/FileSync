package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.FoldersImpl
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.persistence.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDateTime
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.LocalDateTime
import kotlin.collections.set


class SyncFiles(private val syncFilesParams: SyncFilesParams) {

    private val backupDir = ".syncFilesHistory"
    private val lockfileName = ".syncFiles_lockfile"
    private val syncResultFilePrefix = ".syncedFiles_"
    private val syncResultFileSuffix = ".jsn"
    private val deletedFilesFilePrefix = ".deletedFiles_"
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

        val syncResultFile = File(sourceDir, "$syncResultFilePrefix${targetDir.path.hashCode()}$syncResultFileSuffix")
        val deletedFilesFile = File(sourceDir, "$deletedFilesFilePrefix${targetDir.path.hashCode()}$deletedFilesFileSuffix")

        val lastSyncResult = if (syncResultFile.exists()) readSyncResult(syncResultFile) else null
        var deletedFiles = if (deletedFilesFile.exists()) readDeletedFiles(deletedFilesFile) else null

        @Suppress("NAME_SHADOWING")
        val filter = Filter(
            fileFilter = { path, fileName ->
                if (fileName.startsWith(syncResultFilePrefix) && fileName.endsWith(syncResultFileSuffix) ||
                    fileName.startsWith(deletedFilesFilePrefix) && fileName.endsWith(deletedFilesFileSuffix) ||
                    fileName == lockfileName
                )
                    ExcludedBy.SYSTEM
                else
                    filter.fileFilter.excluded(path, fileName)
            },
            folderFilter = { fullPath, folderName ->
                if (folderName == backupDir)
                    ExcludedBy.SYSTEM
                else
                    filter.folderFilter.excluded(fullPath, folderName)
            }
        )


        val sourceChanges: MutableChanges
        val targetChanges: MutableChanges
        val syncResultFiles: MutableSet<File2>

        val folders = FoldersImpl()
        with(MutableFoldersContext(folders)) {
            val lastSyncResultFiles = lastSyncResult?.mapToRead(filter) ?: listOf()
            syncResultFiles = lastSyncResultFiles.toMutableSet()
            if (lastSyncResultFiles.size != syncResultFiles.size) throw Exception()

            if (syncFilesParams.parallelIndexing) {
                runBlocking {
                    val sourceChangesDeferred = async { getChanges(sourceDir, lastSyncResultFiles, filter) }
                    val targetChangesDeferred = async { getChanges(targetDir, lastSyncResultFiles, filter) }
                    sourceChanges = sourceChangesDeferred.await()
                    targetChanges = targetChangesDeferred.await()
                }
            } else {
                sourceChanges = getChanges(sourceDir, lastSyncResultFiles, filter)
                targetChanges = getChanges(targetDir, lastSyncResultFiles, filter)
            }
        }

        val failures = mutableListOf<String>()

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
                    .sortedWith(compareBy({ foldersCtx.getFullPath(it.folderId).lowercase() }, { foldersCtx.getFullPath(it.folderId) }, { it.filename }))
                    .forEach {
                        it.action(actionEnv)
                        testIfCancel()
                    }

                if (sourceChanges.deleted.isNotEmpty()) {
                    deletedFiles = DeletedFiles((deletedFiles?.files ?: listOf()) + sourceChanges.deleted.map { it.copy(folderId = 0) })
                }
                if (targetChanges.deleted.isNotEmpty()) {
                    deletedFiles = DeletedFiles((deletedFiles?.files ?: listOf()) + targetChanges.deleted.map { it.copy(folderId = 0) })
                }
            }
        }

        // TODO use
//        val usedFolderIds = buildSet {
//            syncResult.forEach { add(it.folderId) }
//
//            while (true) {
//                val sizeBefore = size
//                folders.folders.values.forEach {
//                    if (it.id in this && it.parentFolderId != null) add(it.parentFolderId)
//                }
//                if (sizeBefore == size) break // no changes; break
//            }
//        }

        val syncResult = SyncResult(
            sourcePath = sourceDir.path,
            targetPath = targetDir.path,
            runDate = now.toKotlinLocalDateTime(),
            files = syncResultFiles.toList(),
            folder = folders.get(folders.rootFolderId),
            failuresOccurred = failures
        )

        if (!syncFilesParams.dryRun) {
            if (syncResultFile.exists()) {
                Files.move(
                    syncResultFile.toPath(),
                    File(sourceDir, syncResultFile.name.replace(syncResultFileSuffix, "_old$syncResultFileSuffix")).toPath(),
                    REPLACE_EXISTING
                )
            }
            saveSyncResult(syncResultFile, syncResult)

            if (deletedFiles != null) {
                if (deletedFilesFile.exists()) {
                    Files.move(
                        deletedFilesFile.toPath(),
                        File(sourceDir, deletedFilesFile.name.replace(deletedFilesFileSuffix, "_old$deletedFilesFileSuffix")).toPath(),
                        REPLACE_EXISTING
                    )
                }
                saveDeletedFiles(deletedFilesFile, deletedFiles!!)
            }
        }
    }


    context(MutableFoldersContext)
    private fun SyncResult.mapToRead(filter: Filter): List<File2> {
        val mapping = mutableMapOf<Long, Long>()
        mapping[foldersCtx.rootFolderId] = foldersCtx.rootFolderId

        fun sync(folder: Folder, parentFolderId: Long) {
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
