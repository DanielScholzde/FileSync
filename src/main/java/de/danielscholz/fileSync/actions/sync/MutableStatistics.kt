package de.danielscholz.fileSync.actions.sync


class MutableStatisticsContext(
    val statisticsCtx: MutableStatistics
)

class MutableStatistics {
    var files = 0
    var folders = 0
    var hashCalculated = 0
}