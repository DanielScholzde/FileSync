package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.persistence.FileEntity


context(FoldersContext, CaseSensitiveContext)
fun checkAndFix(
    sourceChanges: MutableChanges,
    targetChanges: MutableChanges,
    currentFilesSource: MutableCurrentFiles,
    currentFilesTarget: MutableCurrentFiles,
    syncResultFiles: MutableSet<FileEntity>
): Boolean {

    val failures = mutableListOf<String>()

    fun Collection<FileEntity>.ifNotEmptyCreateConflicts(detailMsg: String) {
        forEach {
            failures += "${it.pathAndName()} $detailMsg"
        }
    }

    fun Collection<IntersectResult<FileEntity>>.ifNotEmptyCreateConflicts(detailMsg: String, diffExtractor: (FileEntity) -> String) {
        forEach {
            failures += "${it.left.pathAndName()} $detailMsg: ${diffExtractor(it.left)} != ${diffExtractor(it.right)}"
        }
    }

    // fix scenario: same added/changed files in both locations
    equalsBy(pathAndName) {
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
                listOf(pair).ifNotEmptyCreateConflicts("changed modification date (not equal) within source and target") {
                    it.modified.toStr()
                }
            }
        }

    // fix scenario: same deleted files in both locations
    equalsBy(pathAndName) {
        sourceChanges.deleted intersect targetChanges.deleted
    }
        .forEach { (source, target) ->
            syncResultFiles.removeWithCheck(source)
            sourceChanges.deleted.removeWithCheck(source)
            targetChanges.deleted.removeWithCheck(target)
        }

    // fix scenario: same moved files in both locations
    equalsByTo(pathAndName) {
        sourceChanges.movedOrRenamed intersect targetChanges.movedOrRenamed
    }
        .forEach { (sourceChange, targetChange) ->
            equalsBy(MatchMode.PATH + MatchMode.FILENAME + MatchMode.HASH + MatchMode.MODIFIED) {
                if (sourceChange.from eq targetChange.from &&
                    sourceChange.to eq targetChange.to
                ) {
                    // here: sourceChange must be identical to targetChange! (except for folderId, which can be different because of upper/lower case changes)
                    syncResultFiles.remove(sourceChange.from) || syncResultFiles.remove(targetChange.from) || throw IllegalStateException()
                    syncResultFiles.addWithCheck(sourceChange.to)
                    sourceChanges.movedOrRenamed.removeWithCheck(sourceChange)
                    targetChanges.movedOrRenamed.removeWithCheck(targetChange)
                }
            }
        }

    equalsBy(pathAndName) {
        sourceChanges.contentChanged.to() intersect targetChanges.contentChanged.to()
    }
        .filter(HASH_NEQ)
        .ifNotEmptyCreateConflicts("modified (with different content) within source and target") {
            "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
        }

    equalsBy(pathAndName) {
        sourceChanges.modifiedChanged.to() intersect targetChanges.modifiedChanged.to()
    }
        .filter(MODIFIED_NEQ)
        .ifNotEmptyCreateConflicts("changed modification date (not equal) within source and target") {
            it.modified.toStr()
        }


    fun directionalChecks(sourceChanges: MutableChanges, targetChanges: MutableChanges, currentFilesTarget: Set<FileEntity>) {
        equalsBy(pathAndName) {
            (sourceChanges.added intersect currentFilesTarget)
                .filter(HASH_NEQ or MODIFIED_NEQ)
                .ifNotEmptyCreateConflicts("already exists within target dir (and has different content or modification date)") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (sourceChanges.deleted intersect targetChanges.contentChanged.to())
                .ifNotEmptyCreateConflicts("deleted source file but changed content within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (sourceChanges.contentChanged.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts("content changed file does not exists within target dir")

            (sourceChanges.movedOrRenamed.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts("source of moved file does not exists within target dir")

            (sourceChanges.movedAndContentChanged.from() - currentFilesTarget)
                .ifNotEmptyCreateConflicts("source of moved file does not exists within target dir")

            (sourceChanges.movedOrRenamed.to() intersect currentFilesTarget)
                .ifNotEmptyCreateConflicts("target of moved file already exists within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (sourceChanges.movedAndContentChanged.to() intersect currentFilesTarget)
                .ifNotEmptyCreateConflicts("target of moved file already exists within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }
        }
    }

    directionalChecks(sourceChanges, targetChanges, currentFilesTarget.files)
    directionalChecks(targetChanges, sourceChanges, currentFilesSource.files)

    if (failures.isNotEmpty()) {
        println("Conflicts:\n${failures.joinToString("\n")}")
        return false
    }
    return true
}
