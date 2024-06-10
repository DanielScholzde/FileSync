package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.persistence.File2


class Action(
    val folderId: Long,
    val filename: String,
    val action: ActionEnv.() -> Unit,
)

class ActionEnv(
    val syncResultFiles: MutableSet<File2>,
    private val failures: MutableList<String>,
    private val dryRun: Boolean
) {
    fun process(action: String, files: String, block: () -> Unit) {
        try {
            print("$action:".padEnd(14) + files)
            if (!dryRun) {
                block()
            }
            println(" ok")
        } catch (e: Exception) {
            val failure = ": " + e.message + " (" + e::class.simpleName + ")"
            println(failure)
            failures += action + failure
        }
    }
}