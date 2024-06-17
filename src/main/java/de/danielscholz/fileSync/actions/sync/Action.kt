package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.getBasicFileAttributes
import de.danielscholz.fileSync.common.toKotlinInstantIgnoreMillis
import de.danielscholz.fileSync.persistence.FileEntity
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
    private val failures: MutableList<String>,
    private val dryRun: Boolean
) {
    private val processEnv = ProcessEnv()

    fun process(action: String, files: String, block: ProcessEnv.() -> Unit) {
        try {
            print("$action:".padEnd(14) + files)
            if (!dryRun) {
                processEnv.block()
            }
            println(" ok")
        } catch (e: Exception) {
            val failure = ": " + e.message + " (" + e::class.simpleName + ")"
            println(failure)
            failures += action + failure
        }
    }
}


class ProcessEnv {

    fun checkIsUnchanged(file: File, attributes: FileEntity) {
        getBasicFileAttributes(file).let {
            if (it.lastModifiedTime().toKotlinInstantIgnoreMillis() != attributes.modified || it.size() != attributes.size) {
                throw Exception("File ${file.name} has changed since indexing!")
            }
        }
    }

}