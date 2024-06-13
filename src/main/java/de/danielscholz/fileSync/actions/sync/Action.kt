package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.common.getBasicFileAttributes
import de.danielscholz.fileSync.common.toKotlinInstantIgnoreMillis
import de.danielscholz.fileSync.persistence.File2
import java.io.File


class Action(
    val folderId: Long,
    val filename: String,
    val priority: Int = 0,
    val action: ActionEnv.() -> Unit,
)

class ActionEnv(
    val syncResultFiles: MutableSet<File2>,
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
    fun checkIsUnchanged(file: File, attributes: File2) {
        getBasicFileAttributes(file).let {
            if (it.lastModifiedTime().toKotlinInstantIgnoreMillis() != attributes.modified || it.size() != attributes.size) {
                throw Exception("File has changed since indexing!")
            }
        }
    }
}