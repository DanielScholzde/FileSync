package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.ui.UI


interface Statistics {
    val filesCount: Int
    val foldersCount: Int
    val filesHashCalculatedCount: Int
    val filesHashCalculatedSize: Long
}

class MutableStatistics(private val uiDir: UI.Dir) : Statistics {
    override var filesCount = 0
    override var foldersCount = 0
    override var filesHashCalculatedCount = 0
        set(value) {
            field = value
            uiDir.filesHashCalculatedCount = value
        }
    override var filesHashCalculatedSize = 0L
        set(value) {
            field = value
            uiDir.filesHashCalculatedSize = value
        }
}