package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.FileSystemEncryption
import de.danielscholz.fileSync.persistence.FileEntity
import de.danielscholz.fileSync.ui.UI
import java.io.File


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
    private val dryRun: Boolean,
    fs: FileSystemEncryption
) {
    private val processEnv = ProcessEnv(fs)
    var successfullyRealProcessed = 0

    fun process(action: String, files: String, block: ProcessEnv.() -> Unit) {
        print("$action:".padEnd(14) + files)
        UI.addCurrentOperation("$action $files")
        var result: String
        if (!dryRun) {
            try {
                processEnv.block() // execute action; may throw an exception!
                successfullyRealProcessed++
                result = " ok"
            } catch (e: Exception) {
                result = ": " + e.message + " (" + e::class.simpleName + ")"
                addFailure(action + result)
            }
        } else {
            result = " ok (dry-run)"
        }
        println(result)
        UI.addSuffixToLastOperation(result)
    }
}


class ProcessEnv(private val fs: FileSystemEncryption) {

    fun checkIsUnchanged(file: File, attributes: FileEntity) {
        fs.checkIsUnchanged(file, attributes)
    }

    fun bytesCopied(bytes: Long) {
        UI.currentBytesCopied += bytes
    }

}