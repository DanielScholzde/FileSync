package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.actions.FoldersImpl
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.matching.MatchMode.*
import de.danielscholz.fileSync.persistence.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinInstant
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
    private val syncResultFilePrefix = ".syncedFiles_"
    private val syncResultFileSuffix = ".jsn"
    private val deletedFilesFilePrefix = ".deletedFiles_"
    private val deletedFilesFileSuffix = ".jsn"

    fun sync(sourceDir: File, targetDir: File, filter: Filter) {
        guardWithLockFile(File(syncFilesParams.lockfileDir ?: targetDir, lockfileName)) {
            syncIntern(sourceDir, targetDir, filter)
        }
    }

    private fun syncIntern(sourceDir: File, targetDir: File, filter: Filter) {
        println("Source dir: $sourceDir")
        println("Target dir: $targetDir\n")

        val now = LocalDateTime.now().withNano(0)
        val dateTimeStr = now.toString().replace(":", "").replace("T", " ")
        val changedDir = "$backupDir/modified/$dateTimeStr"
        val deletedDir = "$backupDir/deleted/$dateTimeStr"

        val folders = FoldersImpl()

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
                if (fileName.startsWith(syncResultFilePrefix) && fileName.endsWith(syncResultFileSuffix) || fileName == lockfileName)
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


        val sourceChanges: Changes
        val targetChanges: Changes
        val syncResultFiles: MutableSet<File2>

        with(MutableFoldersContext(folders)) {
            val lastSyncResultFiles = lastSyncResult?.mapToRead(filter) ?: listOf()
            syncResultFiles = lastSyncResultFiles.toMutableSet()
            if (lastSyncResultFiles.size != syncResultFiles.size) throw Exception()

            runBlocking {
                val sourceChangesDeferred = async { getChanges(sourceDir, lastSyncResultFiles, filter) }
                val targetChangesDeferred = async { getChanges(targetDir, lastSyncResultFiles, filter) }
                sourceChanges = sourceChangesDeferred.await()
                targetChanges = targetChangesDeferred.await()
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

                val actions = mutableListOf<Action>()

                fun Changes.createActions(sourceDir: File, targetDir: File) {

                    fun process(action: String, files: String, block: () -> Unit) {
                        try {
                            print("$action:".padEnd(14) + files)
                            if (!syncFilesParams.dryRun) {
                                block()
                            }
                            println(" ok")
                        } catch (e: Exception) {
                            val failure = ": " + e.message + " (" + e::class.simpleName + ")"
                            println(failure)
                            failures += action + failure
                        }
                    }

                    added.forEach {
                        actions += Action(it.folderId, it.name) {
                            val sourceFile = File(sourceDir, it.pathAndName())
                            val targetFile = File(targetDir, it.pathAndName())
                            process("add", "$sourceFile -> $targetFile") {
                                targetFile.parentFile.mkdirs()
                                Files.copy(sourceFile.toPath(), targetFile.toPath(), COPY_ATTRIBUTES)
                                syncResultFiles.addWithCheck(it)
                            }
                        }
                    }

                    contentChanged.forEach { (_, to) ->
                        // pathAndName() must be equals in 'from' and 'to'
                        actions += Action(to.folderId, to.name) {
                            val sourceFile = File(sourceDir, to.pathAndName())
                            val targetFile = File(targetDir, to.pathAndName())
                            process("copy", "$sourceFile -> $targetFile") {
                                val backupFile = File(File(targetDir, changedDir), to.pathAndName())
                                backupFile.parentFile.mkdirs()
                                Files.move(targetFile.toPath(), backupFile.toPath())
                                Files.copy(sourceFile.toPath(), targetFile.toPath(), COPY_ATTRIBUTES)
                                syncResultFiles.replace(to)
                            }
                        }
                    }

                    modifiedChanged.forEach { (_, to) ->
                        actions += Action(to.folderId, to.name) {
                            val sourceFile = File(sourceDir, to.pathAndName())
                            val targetFile = File(targetDir, to.pathAndName())
                            process("modified attr", "$sourceFile -> $targetFile") {
                                targetFile.setLastModified(sourceFile.lastModified()) || throw Exception("set of last modification date failed!")
                                syncResultFiles.replace(to)
                            }
                        }
                    }

                    movedOrRenamed.forEach {
                        val (from, to) = it
                        actions += Action(to.folderId, to.name) {
                            val sourceFile = File(targetDir, from.pathAndName())
                            val targetFile = File(targetDir, to.pathAndName())
                            val s = if (it.moved && it.renamed) "move+rename" else if (it.moved) "move" else "rename"
                            process(s, "$sourceFile -> $targetFile") {
                                targetFile.parentFile.mkdirs()
                                Files.move(sourceFile.toPath(), targetFile.toPath())
                                syncResultFiles.removeWithCheck(from)
                                syncResultFiles.addWithCheck(to)
                            }
                        }
                    }

                    deleted.forEach {
                        actions += Action(it.folderId, it.name) {
                            val toDelete = File(targetDir, it.pathAndName())
                            val backupFile = File(File(targetDir, deletedDir), it.pathAndName())
                            process("delete", "$toDelete") {
                                backupFile.parentFile.mkdirs()
                                Files.move(toDelete.toPath(), backupFile.toPath())
                                syncResultFiles.removeWithCheck(it)
                            }
                        }
                    }
                }

                sourceChanges.createActions(sourceDir, targetDir)
                targetChanges.createActions(targetDir, sourceDir)

                actions
                    .sortedWith(compareBy({ foldersCtx.get(it.folderId)!!.fullPath }, { it.filename }))
                    .forEach { it.action() }

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
            targetDir.path,
            now.toKotlinLocalDateTime(),
            syncResultFiles.toList(),
            folders.get(folders.rootFolderId)!!,
            failures
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
    private fun getChanges(dir: File, lastSyncResultFiles: List<File2>, filter: Filter): Changes {
        val caseSensitiveContext = CaseSensitiveContext(
            isCaseSensitiveFileSystem(dir) ?: throw Exception("Unable to determine if filesystem $dir is case sensitive!")
        )
        return with(caseSensitiveContext) {

            val current = getCurrentFiles(dir, filter, lastSyncResultFiles)

            @Suppress("ConvertArgumentToSet")
            val added = equalsBy(pathAndName) {
                (current - lastSyncResultFiles).toMutableSet()
            }

            @Suppress("ConvertArgumentToSet")
            val deleted = equalsBy(pathAndName) {
                (lastSyncResultFiles - current).toMutableSet()
            }

            val movedOrRenamed = mutableSetOf<MovedOrRenamed>()

            equalsBy(PATH + HASH + MODIFIED, true) {
                (deleted intersect added)
                    .map { MovedOrRenamed(it.left, it.right) }
                    .ifNotEmpty {
                        deleted -= it.from().toSet()
                        added -= it.to().toSet()
                        movedOrRenamed += it
                    }
            }

            equalsBy(FILENAME + HASH + MODIFIED, true) {
                (deleted intersect added)
                    .map { MovedOrRenamed(it.left, it.right) }
                    .ifNotEmpty {
                        deleted -= it.from().toSet()
                        added -= it.to().toSet()
                        movedOrRenamed += it
                    }
            }

            equalsBy(HASH + MODIFIED, true) {
                (deleted intersect added)
                    .map { MovedOrRenamed(it.left, it.right) }
                    .ifNotEmpty {
                        deleted -= it.from().toSet()
                        added -= it.to().toSet()
                        movedOrRenamed += it
                    }
            }

            val contentChanged = equalsBy(pathAndName) {
                (lastSyncResultFiles intersect current)
                    .filter(HASH_NEQ)
                    .map { ContentChanged(it.left, it.right) }
                    .toMutableSet()
            }

            val modifiedChanged = equalsBy(pathAndName) {
                (lastSyncResultFiles intersect current)
                    .filter(HASH_EQ and MODIFIED_NEQ)
                    .map { ModifiedChanged(it.left, it.right) }
                    .toMutableSet()
            }

            Changes(
                added = added,
                deleted = deleted,
                contentChanged = contentChanged,
                modifiedChanged = modifiedChanged,
                movedOrRenamed = movedOrRenamed,
                allFilesBeforeSync = current.toSet()
            )
        }
    }


    context(MutableFoldersContext)
    private fun getCurrentFiles(dir: File, filter: Filter, lastSyncResult: List<File2>): List<File2> {

        val files = mutableListOf<File2>()

        val lastSyncMap = lastSyncResult.associateBy { (it.folderId to it.name) }

        fun process(folderResult: FolderResult, folderId: Long) {
            folderResult.files
                .filter { filter.fileFilter.excluded(it.path, it.name) == null }
                .forEach { file ->

                    val lastCalculatedHash = lastSyncMap[folderId to file.name]?.let { lastIndexedFile ->
                        if (lastIndexedFile.size == file.size && lastIndexedFile.modified == file.modified) {
                            lastIndexedFile.hash
                        } else null
                    }

                    files += File2(
                        hash = lastCalculatedHash ?: file.hash.value?.let { FileHash(java.time.Instant.now().toKotlinInstant(), it) },
                        folderId = folderId,
                        name = file.name,
                        created = file.created,
                        modified = file.modified,
                        hidden = file.hidden,
                        size = file.size
                    )

                    testIfCancel()
                }

            folderResult.folders
                .filter {
                    val excludedBy = filter.folderFilter.excluded(it.fullPath, it.name)
                    if (excludedBy == ExcludedBy.USER) println("$dir${it.fullPath} (excluded)")
                    excludedBy == null
                }
                .forEach {
                    val folder = foldersCtx.getOrCreate(it.name, folderId)
                    process(it.content(), folder.id)
                }
        }

        process(readDir(dir), foldersCtx.rootFolderId)

        return files
    }


    context(MutableFoldersContext)
    private fun SyncResult.mapToRead(filter: Filter): List<File2> {
        val mapping = mutableMapOf<Long, Long>()
        mapping[foldersCtx.rootFolderId] = foldersCtx.rootFolderId

        fun sync(folder: Folder, parentFolderId: Long) {
            if (filter.folderFilter.excluded(folder.fullPath, folder.name) != null) {
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

    private class Action(
        val folderId: Long,
        val filename: String,
        val action: () -> Unit,
    )

}
