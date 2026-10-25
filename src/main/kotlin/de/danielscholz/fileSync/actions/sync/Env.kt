package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.ui.UI
import java.io.File


class Env(
    val name: String,
    val dir: File,
    val indexedFilesFile: File,
    val deletedFilesFile: File,
    val caseSensitive: Boolean,
    val uiDir: UI.Dir,
    val encryptPaths: List<PathMatcher>,
    val password: String?
) {
    val mutableStatistics = MutableStatistics(uiDir)
    val statistics: Statistics = mutableStatistics
}