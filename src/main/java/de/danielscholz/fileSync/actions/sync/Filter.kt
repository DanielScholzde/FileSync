package de.danielscholz.fileSync.actions.sync


enum class ExcludedBy {
    USER,
    SYSTEM,
}

fun interface FolderFilter {
    // folderName is last part of path
    fun excluded(fullPath: String, folderName: String): ExcludedBy? // result == null -> include folder
}

fun interface FileFilter {
    // path does not include fileName!
    fun excluded(path: String, fileName: String): ExcludedBy? // result == null -> include file
}

class Filter(
    val folderFilter: FolderFilter,
    val fileFilter: FileFilter,
)