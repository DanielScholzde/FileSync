package de.danielscholz.fileSync.actions

import de.danielscholz.fileSync.SyncFilesParams
import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.matching.MatchMode.*
import de.danielscholz.fileSync.persistence.*
import kotlinx.datetime.Instant
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

    fun sync(sourceDir: File, targetDir: File, filter: Filter) {
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

        val filePrefix = ".syncFiles_"
        val fileSuffix = ".jsn"
        val indexRunFile = File(sourceDir, "$filePrefix${targetDir.path.hashCode()}$fileSuffix")

        @Suppress("NAME_SHADOWING")
        val filter = Filter(
            fileFilter = { path, fileName ->
                if (fileName.startsWith(filePrefix) && fileName.endsWith(fileSuffix))
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

        val lastIndexRun = if (indexRunFile.exists()) readIndexRun(indexRunFile) else null

        val sourceChanges: Changes
        val targetChanges: Changes
        val syncResult: MutableSet<File2>

        with(MutableFoldersContext(folders)) {
            val lastSyncResult = lastIndexRun?.mapToRead(filter) ?: listOf()
            syncResult = lastSyncResult.toMutableSet()
            if (lastSyncResult.size != syncResult.size) throw Exception()

            sourceChanges = getChanges(sourceDir, lastSyncResult, filter)
            targetChanges = getChanges(targetDir, lastSyncResult, filter)
        }

        val failures = mutableListOf<String>()

        with(FoldersContext(folders)) {
            with(caseSensitiveContext) {

                if (!checkAndFix(sourceChanges, targetChanges, syncResult)) {
                    return
                }

                if (!furtherChecks(sourceDir, targetDir, sourceChanges, targetChanges, syncFilesParams)) {
                    return
                }

                val actions = mutableListOf<Action>()

                fun Changes.createActions(sourceDir: File, targetDir: File) {

                    fun process(action: String, block: () -> Unit) {
                        try {
                            print(action)
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
                            process("add: $sourceFile -> $targetFile") {
                                targetFile.parentFile.mkdirs()
                                Files.copy(sourceFile.toPath(), targetFile.toPath(), COPY_ATTRIBUTES)
                                syncResult.addWithCheck(it)
                            }
                        }
                    }

                    contentChanged.forEach { (_, to) ->
                        // pathAndName() must be equals in 'from' and 'to'
                        actions += Action(to.folderId, to.name) {
                            val sourceFile = File(sourceDir, to.pathAndName())
                            val targetFile = File(targetDir, to.pathAndName())
                            process("copy: $sourceFile -> $targetFile") {
                                val backupFile = File(File(targetDir, changedDir), to.pathAndName())
                                backupFile.parentFile.mkdirs()
                                Files.move(targetFile.toPath(), backupFile.toPath())
                                Files.copy(sourceFile.toPath(), targetFile.toPath(), COPY_ATTRIBUTES)
                                syncResult.replace(to)
                            }
                        }
                    }

                    attributesChanged.forEach {
                        actions += Action(it.folderId, it.name) {
                            val sourceFile = File(sourceDir, it.pathAndName())
                            val targetFile = File(targetDir, it.pathAndName())
                            process("change 'modified': $sourceFile -> $targetFile") {
                                targetFile.setLastModified(sourceFile.lastModified()) || throw Exception("set of last modification date failed!")
                                syncResult.replace(it)
                            }
                        }
                    }

                    movedOrRenamed.forEach {
                        val (from, to) = it
                        actions += Action(to.folderId, to.name) {
                            val sourceFile = File(targetDir, from.pathAndName())
                            val targetFile = File(targetDir, to.pathAndName())
                            val s = if (it.moved && it.renamed) "move+rename" else if (it.moved) "move" else "rename"
                            process("$s: $sourceFile -> $targetFile") {
                                targetFile.parentFile.mkdirs()
                                Files.move(sourceFile.toPath(), targetFile.toPath())
                                syncResult.removeWithCheck(from)
                                syncResult.addWithCheck(to)
                            }
                        }
                    }

                    deleted.forEach {
                        actions += Action(it.folderId, it.name) {
                            val toDelete = File(targetDir, it.pathAndName())
                            val backupFile = File(File(targetDir, deletedDir), it.pathAndName())
                            process("delete: $toDelete") {
                                backupFile.parentFile.mkdirs()
                                Files.move(toDelete.toPath(), backupFile.toPath())
                                syncResult.removeWithCheck(it)
                            }
                        }
                    }
                }

                sourceChanges.createActions(sourceDir, targetDir)
                targetChanges.createActions(targetDir, sourceDir)

                actions
                    .sortedWith(compareBy({ foldersCtx.folders[it.folderId]!!.fullPath }, { it.filename }))
                    .forEach { it.action() }
            }
        }

        // TODO use
        val usedFolderIds = buildSet {
            syncResult.forEach { add(it.folderId) }

            while (true) {
                val sizeBefore = size
                folders.folders.values.forEach {
                    if (it.id in this && it.parentFolderId != null) add(it.parentFolderId)
                }
                if (sizeBefore == size) break // no changes; break
            }
        }

        val indexRun = IndexRun(
            targetDir.path,
            now.toKotlinLocalDateTime(),
            syncResult.toList(),
            folders.folders[folders.rootFolderId]!!,
            failures
        )

        if (!syncFilesParams.dryRun) {
            if (indexRunFile.exists()) {
                Files.move(indexRunFile.toPath(), File(sourceDir, indexRunFile.name.replace(fileSuffix, "_old$fileSuffix")).toPath(), REPLACE_EXISTING)
            }
            saveIndexRun(indexRunFile, indexRun)
        }
    }


    context(MutableFoldersContext)
    private fun getChanges(dir: File, lastSyncResult: List<File2>, filter: Filter): Changes {
        val caseSensitiveContext = CaseSensitiveContext(
            isCaseSensitiveFileSystem(dir) ?: throw Exception("Unable to determine if filesystem $dir is case sensitive!")
        )
        return with(caseSensitiveContext) {

            val current = getCurrentFiles(dir, filter, lastSyncResult)

            val added = equalsBy(pathAndName) {
                (current - lastSyncResult).toMutableSet()
            }

            val deleted = equalsBy(pathAndName) {
                (lastSyncResult - current).toMutableSet()
            }

            val moved = mutableListOf<Moved>()

            equalsBy(PATH + HASH + MODIFIED, true) {
                (deleted intersect added)
                    .map { Moved(it.first, it.second) }
                    .ifNotEmpty {
                        added -= it.to().toSet()
                        deleted -= it.from().toSet()
                        moved += it
                    }
            }

            equalsBy(FILENAME + HASH + MODIFIED, true) {
                (deleted intersect added)
                    .map { Moved(it.first, it.second) }
                    .ifNotEmpty {
                        added -= it.to().toSet()
                        deleted -= it.from().toSet()
                        moved += it
                    }
            }

            equalsBy(HASH + MODIFIED, true) {
                (deleted intersect added)
                    .map { Moved(it.first, it.second) }
                    .ifNotEmpty {
                        added -= it.to().toSet()
                        deleted -= it.from().toSet()
                        moved += it
                    }
            }

            val contentChanged = equalsBy(pathAndName) {
                (lastSyncResult intersect current)
                    .filter2(HASH_NEQ)
                    .map { ContentChanged(it.first, it.second) }
            }

            val attributesChanged = equalsBy(pathAndName) {
                (lastSyncResult intersect current)
                    .filter2(HASH_EQ and MODIFIED_NEQ)
                    .right()
            }

            Changes(
                added,
                deleted,
                contentChanged.toMutableSet(),
                attributesChanged.toSet(),
                moved,
                current.toSet()
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
    private fun IndexRun.mapToRead(filter: Filter): List<File2> {
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

    class Action(
        val folderId: Long,
        val filename: String,
        val action: () -> Unit,
    )

    class Changes(
        val added: MutableSet<File2>,
        val deleted: MutableSet<File2>,
        val contentChanged: MutableSet<ContentChanged>,
        val attributesChanged: Set<File2>,
        val movedOrRenamed: List<Moved>,
        val allFilesBeforeSync: Set<File2>,
    ) {
        init {
            // all sets/collections must be disjoint
            if (added.size + deleted.size + contentChanged.size + attributesChanged.size + 2 * movedOrRenamed.size !=
                (added + deleted + contentChanged + attributesChanged + movedOrRenamed.from() + movedOrRenamed.to()).size
            ) {
                throw IllegalStateException()
            }
        }

        fun hasChanges() = added.isNotEmpty() || deleted.isNotEmpty() || contentChanged.isNotEmpty() || attributesChanged.isNotEmpty() || movedOrRenamed.isNotEmpty()
    }

    interface FromTo {
        val from: File2
        val to: File2
    }

    data class Moved(override val from: File2, override val to: File2) : FromTo {
        val renamed get() = from.name != to.name
        val moved get() = from.folderId != to.folderId
    }

    /** equals/hashCode: only 'to' is considered! */
    data class ContentChanged(override val from: File2, override val to: File2) : FromTo {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ContentChanged) return false
            return (to == other.to)
        }

        override fun hashCode() = to.hashCode()

        companion object {
            val DOES_NOT_MATTER_FILE = File2(0, "-", Instant.DISTANT_PAST, Instant.DISTANT_PAST, true, 0, null)
        }
    }

}
