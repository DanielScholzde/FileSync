package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.persistence.FileEntity
import java.io.File


context(FoldersContext, CaseSensitiveContext)
fun checkAndFix(
    sourceDir: File,
    targetDir: File,
    sourceChanges: MutableChanges,
    targetChanges: MutableChanges,
    currentFilesSource: MutableCurrentFiles,
    currentFilesTarget: MutableCurrentFiles,
    syncResultFiles: MutableSet<FileEntity>
): Boolean {

    val failures = mutableListOf<String>()

    fun Collection<FileEntity>.ifNotEmptyCreateConflicts(rootDir: File, detailMsg: String) {
        forEach {
            failures += "$rootDir${it.pathAndName()} $detailMsg"
        }
    }

    fun Collection<IntersectResult<FileEntity>>.ifNotEmptyCreateConflicts(rootDir: File, detailMsg: String, diffExtractor: (FileEntity) -> String) {
        forEach {
            failures += "$rootDir${it.left.pathAndName()} $detailMsg: ${diffExtractor(it.left)} != ${diffExtractor(it.right)}"
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
                        throw IllegalStateException()
                targetChanges.added.remove(targetTo) ||
                        targetChanges.contentChanged.remove(ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, targetTo)) ||
                        throw IllegalStateException()
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

                listOf(pair).ifNotEmptyCreateConflicts(targetDir, "changed modification date (not equal) within source and target") {
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
                        throw IllegalStateException()
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
        .ifNotEmptyCreateConflicts(targetDir, "modified (with different content) within source and target") {
            "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
        }

    equalsForFileBy(pathAndName) {
        sourceChanges.modifiedChanged.to() intersect targetChanges.modifiedChanged.to()
    }
        .filter(MODIFIED_NEQ)
        .ifNotEmptyCreateConflicts(targetDir, "changed modification date (not equal) within source and target") {
            it.modified.toStr()
        }


    fun directionalChecks(
        sourceDir: File,
        targetDir: File,
        sourceChanges: MutableChanges,
        targetChanges: MutableChanges,
        currentFilesTarget: Set<FileEntity>
    ) {
        equalsForFileBy(pathAndName) {
            (sourceChanges.added intersect currentFilesTarget)
                .filter(HASH_NEQ or MODIFIED_NEQ)
                .ifNotEmptyCreateConflicts(targetDir, "already exists within target dir (and has different content or modification date)") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
                }

            (sourceChanges.deleted intersect targetChanges.contentChanged.to())
                .ifNotEmptyCreateConflicts(targetDir, "deleted source file but changed content within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
                }

            (sourceChanges.contentChanged.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts(sourceDir, "content changed file does not exists within target dir")

            (sourceChanges.movedOrRenamed.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts(sourceDir, "source of moved file does not exists within target dir")

            (sourceChanges.movedAndContentChanged.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts(sourceDir, "source of moved and content changed file does not exists within target dir")

            (sourceChanges.movedOrRenamed.to() intersect currentFilesTarget)
                .ifNotEmptyCreateConflicts(targetDir, "target of moved file already exists within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
                }

            (sourceChanges.movedAndContentChanged.to() intersect currentFilesTarget)
                .ifNotEmptyCreateConflicts(targetDir, "target of moved and content changed file already exists within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.substring(0, 10)}.."
                }
        }
    }

    directionalChecks(sourceDir, targetDir, sourceChanges, targetChanges, currentFilesTarget.files)
    directionalChecks(targetDir, sourceDir, targetChanges, sourceChanges, currentFilesSource.files)

    if (failures.isNotEmpty()) {
        println("Conflicts:\n${failures.joinToString("\n")}")
        return false
    }
    return true
}
