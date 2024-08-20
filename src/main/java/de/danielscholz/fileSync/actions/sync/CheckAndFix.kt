package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.ui.UI
import java.io.File


context(FoldersContext, CaseSensitiveContext)
fun checkAndFix(
    sourceDir: File,
    targetDir: File,
    sourceChanges: MutableChanges,
    targetChanges: MutableChanges,
    currentFilesSource: MutableCurrentFiles,
    currentFilesTarget: MutableCurrentFiles,
    syncResultFiles: MutableSet<FileEntity>,
    fs: FileSystemEncryption
): Boolean {

    val failures = mutableListOf<Triple<String, (() -> Unit)?, (() -> Unit)?>>()

    fun Collection<FileEntity>.ifNotEmptyCreateConflicts(rootDir: File, detailMsg: String) {
        forEach {
            failures += Triple("$rootDir${it.pathAndName()} $detailMsg", null, null)
        }
    }

    fun Collection<IntersectResult<FileEntity>>.ifNotEmptyCreateConflicts(
        leftDir: File,
        rightDir: File,
        detailMsg: String,
        resolveConflict: ((File, File) -> Unit)? = null,
        diffExtractor: (FileEntity) -> String
    ) {
        forEach { res ->
            failures += Triple(
                "$leftDir${res.left.pathAndName()} != $rightDir${res.right.pathAndName()}: $detailMsg: ${diffExtractor(res.left)} != ${diffExtractor(res.right)}",
                resolveConflict?.let { { resolveConflict(File(leftDir, res.left.pathAndName()), File(rightDir, res.right.pathAndName())) } },
                resolveConflict?.let { { resolveConflict(File(rightDir, res.right.pathAndName()), File(leftDir, res.left.pathAndName())) } },
            )
        }
    }

    // fix scenario: same added/changed files in both locations
    equalsForFileBy(pathAndName) {
        sourceChanges.added + sourceChanges.contentChanged.to() intersect targetChanges.added + targetChanges.contentChanged.to()
    }
        .filter(HASH_EQ)
        .forEach { pair ->
            val (sourceTo, targetTo) = pair
            if (sourceTo.modified == targetTo.modified) {
                // here: sourceTo must be identical to targetTo!
                syncResultFiles -= sourceTo // remove old instance (optional)
                syncResultFiles.addWithCheck(sourceTo) // add new with changed hash
                sourceChanges.added.remove(sourceTo) ||
                        // equals of ContentChanged considers only second property
                        sourceChanges.contentChanged.remove(ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, sourceTo)) ||
                        throw Error("Element sourceTo could not be removed!")
                targetChanges.added.remove(targetTo) ||
                        targetChanges.contentChanged.remove(ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, targetTo)) ||
                        throw Error("Element targetTo could not be removed!")
            } else {
                // fix modification date:
//                val f1 = sourceTo.modified.toLocalDateTime(TimeZone.currentSystemDefault()).format(customFormat2)
//                val f2 = targetTo.modified.toLocalDateTime(TimeZone.currentSystemDefault()).format(customFormat2)
//                if (f1 != f2) {
//                    val targetF = File(targetDir, targetTo.pathAndName())
//                    val sourceF = File(sourceDir, sourceTo.pathAndName())
//                    if (f1 in sourceTo.name) {
//                        targetF.setLastModified(sourceF.lastModified())
//
//                        syncResultFiles -= sourceTo // remove old instance (optional)
//                        syncResultFiles.addWithCheck(sourceTo) // add new with changed hash
//                        sourceChanges.added.remove(sourceTo) ||
//                                // equals of ContentChanged considers only second property
//                                sourceChanges.contentChanged.remove(ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, sourceTo)) ||
//                                throw IllegalStateException()
//                        targetChanges.added.remove(targetTo) ||
//                                targetChanges.contentChanged.remove(ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, targetTo)) ||
//                                throw IllegalStateException()
//                    } else if (f1 in targetTo.name) {
//                        sourceF.setLastModified(targetF.lastModified())
//
//                        syncResultFiles -= targetTo // remove old instance (optional)
//                        syncResultFiles.addWithCheck(targetTo) // add new with changed hash
//                        sourceChanges.added.remove(sourceTo) ||
//                                // equals of ContentChanged considers only second property
//                                sourceChanges.contentChanged.remove(ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, sourceTo)) ||
//                                throw IllegalStateException()
//                        targetChanges.added.remove(targetTo) ||
//                                targetChanges.contentChanged.remove(ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, targetTo)) ||
//                                throw IllegalStateException()
//                    }
//                }

                listOf(pair).ifNotEmptyCreateConflicts(sourceDir, targetDir, "changed modification date (not equal) within source and target",
                    { conflictWinningFile, otherFile ->
                        fs.copyLastModified(conflictWinningFile, otherFile)
                    }) {
                    it.modified.toStr()
                }
            }
        }

    // fix scenario: same deleted files in both locations
    equalsForFileBy(pathAndName) {
        sourceChanges.deleted intersect targetChanges.deleted
    }
        .forEach { (source, target) ->
            syncResultFiles.removeWithCheck(source)
            sourceChanges.deleted.removeWithCheck(source)
            targetChanges.deleted.removeWithCheck(target)
        }

    // fix scenario: same moved files in both locations
    equalsForFileChangeToBy(pathAndName) { // only considers 'to' values
        sourceChanges.movedOrRenamed intersect targetChanges.movedOrRenamed
    }
        .forEach { (sourceChange, targetChange) ->
            equalsForFileBy(MatchMode.PATH + MatchMode.FILENAME + MatchMode.HASH + MatchMode.MODIFIED) {
                if (sourceChange.from eq targetChange.from &&
                    sourceChange.to eq targetChange.to
                ) {
                    // here: sourceChange must be identical to targetChange! (except for folderId, which can be different in upper/lower case)
                    if (syncResultFiles.remove(sourceChange.from)) {
                        syncResultFiles.addWithCheck(sourceChange.to)
                    } else if (syncResultFiles.remove(targetChange.from)) {
                        syncResultFiles.addWithCheck(targetChange.to)
                    } else {
                        throw Error()
                    }
                    sourceChanges.movedOrRenamed.removeWithCheck(sourceChange)
                    targetChanges.movedOrRenamed.removeWithCheck(targetChange)
                }
            }
        }

    equalsForFileBy(pathAndName) {
        sourceChanges.contentChanged.to() intersect targetChanges.contentChanged.to()
    }
        .filter(HASH_NEQ)
        .ifNotEmptyCreateConflicts(sourceDir, targetDir, "modified (with different content) within source and target") {
            "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
        }

    equalsForFileBy(pathAndName) {
        sourceChanges.modifiedChanged.to() intersect targetChanges.modifiedChanged.to()
    }
        .filter(MODIFIED_NEQ)
        .ifNotEmptyCreateConflicts(sourceDir, targetDir, "changed modification date (not equal) within source and target",
            { conflictWinningFile, otherFile ->
                fs.copyLastModified(conflictWinningFile, otherFile)
            }) {
            it.modified.toStr()
        }


    fun directionalChecks(
        sourceDir: File,
        targetDir: File,
        sourceChanges: MutableChanges,
        targetChanges: MutableChanges,
        currentFilesTarget: Set<FileEntity>,
        targetEnvName: String,
    ) {
        equalsForFileBy(pathAndName) {
            (sourceChanges.added intersect currentFilesTarget)
                .filter(HASH_NEQ or MODIFIED_NEQ)
                .partition(HASH_NEQ)
                .also { (hashNEQ, modifiedNEQ) ->
                    hashNEQ.ifNotEmptyCreateConflicts(
                        sourceDir, targetDir, "already exists within $targetEnvName dir (and has different content and/or modification date)",
                        { conflictWinningFile, otherFile ->
                            fs.copy(conflictWinningFile, otherFile, null)
                        }) {
                        "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
                    }
                    modifiedNEQ.ifNotEmptyCreateConflicts(
                        sourceDir, targetDir, "already exists within $targetEnvName dir (and has different modification date)",
                        { conflictWinningFile, otherFile ->
                            fs.copyLastModified(conflictWinningFile, otherFile)
                        }) {
                        "modified: ${it.modified.toStr()}"
                    }
                }


            (sourceChanges.deleted intersect targetChanges.contentChanged.to())
                .ifNotEmptyCreateConflicts(sourceDir, targetDir, "deleted source file but changed content within $targetEnvName dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
                }

            (sourceChanges.contentChanged.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts(targetDir, "content changed file does not exists within $targetEnvName dir")

            (sourceChanges.movedOrRenamed.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts(targetDir, "source of moved file does not exists within $targetEnvName dir")

            (sourceChanges.movedAndContentChanged.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts(targetDir, "source of moved and content changed file does not exists within $targetEnvName dir")

            (sourceChanges.movedOrRenamed.to() intersect currentFilesTarget)
                .ifNotEmptyCreateConflicts(sourceDir, targetDir, "target of moved file already exists within $targetEnvName dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
                }

            (sourceChanges.movedAndContentChanged.to() intersect currentFilesTarget)
                .ifNotEmptyCreateConflicts(sourceDir, targetDir, "target of moved and content changed file already exists within $targetEnvName dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
                }
        }
    }

    directionalChecks(sourceDir, targetDir, sourceChanges, targetChanges, currentFilesTarget.files, "target")
    directionalChecks(targetDir, sourceDir, targetChanges, sourceChanges, currentFilesSource.files, "source")

    if (failures.isNotEmpty()) {
        println("Conflicts:\n${failures.joinToString("\n") { it.first }}")
        UI.conflicts = failures
        return false
    }
    return true
}
