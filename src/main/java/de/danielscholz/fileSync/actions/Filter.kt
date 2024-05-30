package de.danielscholz.fileSync.actions


enum class ExcludedBy {
    USER,
    SYSTEM,
}

fun interface FolderFilter {
    // folderName is last part of path
    fun excluded(fullPath: String, folderName: String): ExcludedBy? // true -> include folder
}

fun interface FileFilter {
    // path does not include fileName!
    fun excluded(path: String, fileName: String): ExcludedBy? // true -> include file
}

class Filter(
    val folderFilter: FolderFilter,
    val fileFilter: FileFilter,
)