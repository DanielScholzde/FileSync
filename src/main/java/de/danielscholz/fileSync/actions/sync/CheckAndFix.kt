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
        (sourceChanges.added + sourceChanges.contentChanged.to() intersect targetChanges.added + targetChanges.contentChanged.to())
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
        (sourceChanges.deleted intersect targetChanges.deleted)
            .forEach { (source, target) ->
                syncResultFiles.removeWithCheck(source)
                sourceChanges.deleted.removeWithCheck(source)
                targetChanges.deleted.removeWithCheck(target)
            }

        // fix scenario: same moved files in both locations
        // TODO

        (sourceChanges.contentChanged.to() intersect targetChanges.contentChanged.to())
            .filter(HASH_NEQ)
            .ifNotEmptyCreateConflicts("modified (with different content) within source and target") {
                "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
            }

        (sourceChanges.modifiedChanged.to() intersect targetChanges.modifiedChanged.to())
            .filter(MODIFIED_NEQ)
            .ifNotEmptyCreateConflicts("changed modification date (not equal) within source and target") {
                it.modified.toStr()
            }
    }


    fun directionalChecks(changed1: MutableChanges, changed2: MutableChanges, currentFiles2: Set<FileEntity>) {
        equalsBy(pathAndName) {
            (changed1.added intersect currentFiles2)
                .filter(HASH_NEQ or MODIFIED_NEQ)
                .ifNotEmptyCreateConflicts("already exists within target dir (and has different content or modification date)") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (changed1.deleted intersect changed2.contentChanged.to())
                .ifNotEmptyCreateConflicts("deleted source file but changed content within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (changed1.contentChanged.from() - currentFiles2)
                .ifNotEmptyCreateConflicts("content changed file does not exists within target dir")

            (changed1.movedOrRenamed.from() - currentFiles2)
                .ifNotEmptyCreateConflicts("source of moved file does not exists within target dir")

            (changed1.movedAndContentChanged.from() - currentFiles2)
                .ifNotEmptyCreateConflicts("source of moved file does not exists within target dir")

            (changed1.movedOrRenamed.to() intersect currentFiles2)
                .ifNotEmptyCreateConflicts("target of moved file already exists within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (changed1.movedAndContentChanged.to() intersect currentFiles2)
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
