package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.ui.UI


class MutableStatisticsContext(
    val statisticsCtx: MutableStatistics
)

class MutableStatistics(private val uiDir: UI.Dir) {
    var filesCount = 0
    var foldersCount = 0
    var filesHashCalculatedCount = 0
        set(value) {
            field = value
            uiDir.filesHashCalculatedCount = value
        }
    var filesHashCalculatedSize = 0L
        set(value) {
            field = value
            uiDir.filesHashCalculatedSize = value
        }
}