package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.*
import de.danielscholz.fileSync.matching.*
import de.danielscholz.fileSync.persistence.File2


context(FoldersContext, CaseSensitiveContext)
fun checkAndFix(sourceChanges: Changes, targetChanges: Changes, syncResult: MutableSet<File2>): Boolean {

    val failures = mutableListOf<String>()

    fun Collection<File2>.ifNotEmptyCreateConflicts(detailMsg: String) {
        forEach {
            failures += "${it.pathAndName()} $detailMsg"
        }
    }

    fun Collection<Pair<File2, File2>>.ifNotEmptyCreateConflicts(detailMsg: String, diffExtractor: (File2) -> String) {
        forEach {
            failures += "${it.first.pathAndName()} $detailMsg: ${diffExtractor(it.first)} != ${diffExtractor(it.second)}"
        }
    }

    // fix scenario: same added files in both locations (or changed files with same content)
    equalsBy(pathAndName) {
        (sourceChanges.added + sourceChanges.contentChanged.to() intersect targetChanges.added + targetChanges.contentChanged.to())
            .filter(HASH_EQ)
            .forEach { pair ->
                val (source, target) = pair
                if (source.modified == target.modified) {
                    syncResult -= source // remove old instance (optional)
                    syncResult += source // add new with changed hash
                    sourceChanges.added -= source
                    sourceChanges.contentChanged -= ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, source) // equals of ContentChanged considers only second property
                    targetChanges.added -= target
                    targetChanges.contentChanged -= ContentChanged(ContentChanged.DOES_NOT_MATTER_FILE, target)
                } else {
                    listOf(pair).ifNotEmptyCreateConflicts("changed modification date (not equal) within source and target") {
                        it.modified.toStr()
                    }
                }
            }

        // fix scenario: same deleted files in both locations
        (sourceChanges.deleted intersect targetChanges.deleted)
            .forEach { (source, target) ->
                syncResult.removeWithCheck(source)
                sourceChanges.deleted -= source
                targetChanges.deleted -= target
            }

        // fix scenario: same moved files in both locations
        // TODO

        (sourceChanges.contentChanged.to() intersect targetChanges.contentChanged.to())
            .filter(HASH_NEQ)
            .ifNotEmptyCreateConflicts("modified (with different content) within source and target") {
                "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
            }

        (sourceChanges.attributesChanged intersect targetChanges.attributesChanged)
            .filter(MODIFIED_NEQ)
            .ifNotEmptyCreateConflicts("changed modification date (not equal) within source and target") {
                it.modified.toStr()
            }
    }


    fun directionalChecks(changed1: Changes, changed2: Changes) {
        equalsBy(pathAndName) {
            (changed1.added intersect changed2.allFilesBeforeSync)
                .filter(HASH_NEQ or MODIFIED_NEQ)
                .ifNotEmptyCreateConflicts("already exists within target dir (and has different content or modification date)") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (changed1.deleted intersect changed2.contentChanged.to())
                .ifNotEmptyCreateConflicts("deleted source file but changed content within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }

            (changed1.movedOrRenamed.from() - changed2.allFilesBeforeSync)
                .ifNotEmptyCreateConflicts("source of moved file does not exists within target dir")

            (changed1.movedOrRenamed.to() intersect changed2.allFilesBeforeSync)
                .ifNotEmptyCreateConflicts("target of moved file already exists within target dir") {
                    "size: ${it.size.formatAsFileSize()}, modified: ${it.modified.toStr()}, hash: ${it.hash?.hash?.substring(0, 10)}.."
                }
        }
    }

    directionalChecks(sourceChanges, targetChanges)
    directionalChecks(targetChanges, sourceChanges)

    if (failures.isNotEmpty()) {
        println("Conflicts:\n${failures.joinToString("\n")}")
        return false
    }
    return true
}
