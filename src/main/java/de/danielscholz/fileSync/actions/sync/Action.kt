package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.FileSystemEncryption
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.ui.UI
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime


enum class Location { SOURCE, TARGET }

class Action(
    val folderId: Long,
    val filename: String,
    val locationOfChangesToBeMade: Location,
    val switchedSourceAndTarget: Boolean,
    val priority: Int = 0,
    val action: ActionEnv.() -> Unit,
)

class ActionEnv(
    val sourceDir: File, // sourceDir and targetDir may be switched, if switchedSourceAndTarget==true
    val targetDir: File,
    val changedDir: String,
    val deletedDir: String,
    val syncResultFiles: MutableSet<FileEntity>,
    val currentFilesTarget: MutableSet<FileEntity>, // may be currentFilesSource, if switchedSourceAndTarget==true
    private val addFailure: (String) -> Unit,
    private val fs: FileSystemEncryption
) {
    var successfullyRealProcessed = 0

    fun processDir(action: String, files: String, block: ProcessEnv.() -> Unit) {
        process(action, files, false, block)
    }

    fun process(action: String, files: String, emptyFile: Boolean, block: ProcessEnv.() -> Unit) {
        print("$action:".padEnd(14) + files)
        UI.addCurrentOperation("$action $files")
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