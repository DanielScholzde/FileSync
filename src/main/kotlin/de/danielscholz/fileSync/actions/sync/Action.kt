package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.fs.FileSystemEncryption
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.ui.UI
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


enum class Location { SOURCE, TARGET }

class Action(
    val folderId: Long,
    val filename: String,
    val locationOfChangesToBeMade: de.danielscholz.fileSync.actions.sync.Location,
    val switchedSourceAndTarget: Boolean,
    val priority: Int = 0,
    val action: de.danielscholz.fileSync.actions.sync.ActionEnv.() -> Unit,
)

class ActionEnv(
    val sourceDir: File, // sourceDir and targetDir may be switched, if switchedSourceAndTarget==true
    val targetDir: File,
    val changedDir: String,
    val deletedDir: String,
    val syncResultFiles: MutableSet<de.danielscholz.fileSync.persistence.FileEntity>,
    val currentFilesTarget: MutableSet<de.danielscholz.fileSync.persistence.FileEntity>, // may be currentFilesSource, if switchedSourceAndTarget==true
    private val addFailure: (String) -> Unit,
    private val fs: de.danielscholz.fileSync.common.fs.FileSystemEncryption
) {
    var successfullyRealProcessed = 0

    fun processDir(action: String, files: String, block: de.danielscholz.fileSync.actions.sync.ProcessEnv.() -> Unit) {
        process(action, files, false, block)
    }

    fun process(action: String, files: String, emptyFile: Boolean, block: de.danielscholz.fileSync.actions.sync.ProcessEnv.() -> Unit) {
        print("$action:".padEnd(14) + files)
        _root_ide_package_.de.danielscholz.fileSync.ui.UI.addCurrentOperation("$action $files")
        var result: String
        try {
            val processEnv = ProcessEnv(fs)
            val duration = measureTime {
                processEnv.block() // execute action; may throw an exception!
            }
            successfullyRealProcessed++
            result = " ok" +
                    (if (processEnv.encrypted) " (encrypted)" else "") +
                    (if (emptyFile) " (empty file)" else "") +
                    (if (fs.dryRun) " (dry-run)" else "") +
                    (if (duration > 1.seconds) " duration: $duration" else "")
        } catch (e: Exception) {
            val stacktrace = e.stackTraceToString().split('\n').take(5).joinToString(" | ") { it.trim() }
            result = ": " + e.message + " (" + e::class.simpleName + ") " + stacktrace
            addFailure(action + result)
        }
        println(result)
        UI.addSuffixToLastOperation(result)
    }
}


class ProcessEnv(val fs: FileSystemEncryption) {

    var encrypted = false
        private set

    fun checkIsUnchanged(file: File, attributes: FileEntity) {
        fs.checkIsUnchanged(file, attributes.modified, attributes.size)
    }

    fun bytesCopied(bytes: Long) {
        UI.currentBytesCopied += bytes
    }

    fun FileSystemEncryption.State.processResult() {
        if (this == FileSystemEncryption.State.ENCRYPTED) {
            encrypted = true
        }
    }

}