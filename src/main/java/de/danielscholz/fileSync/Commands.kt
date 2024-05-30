package de.danielscholz.fileSync

enum class Commands(val command: String) {
    SYNC_FILES("sync"),
    BACKUP_FILES("backup"),
    VERIFY_FILES("verify"),
}