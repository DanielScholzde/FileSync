package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.Global
import kotlinx.datetime.*
import kotlinx.datetime.format.char
import org.slf4j.LoggerFactory
import kotlin.math.absoluteValue


fun registerShutdownCallback(exitCallback: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            exitCallback()
        }
    })
}

fun testIfCancel() {
    if (Global.cancel) {
        LoggerFactory.getLogger("Main").debug("cancel -> exit..")
        throw CancelException()
    }
}

//fun setRootLoggerLevel() {
//    val rootLogger = LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
//    rootLogger.level = when (Config.INST.logLevel.lowercase()) {
//        "info" -> ch.qos.logback.classic.Level.INFO
//        "debug" -> ch.qos.logback.classic.Level.DEBUG
//        "trace" -> ch.qos.logback.classic.Level.TRACE
//        else -> rootLogger.level
//    }
//}


val customFormat = LocalDateTime.Format {
    dayOfMonth()
    char('.')
    monthNumber()
    char('.')
    year()
    char(' ')
    hour()
    char(':')
    minute()
    char(':')
    second()
}


fun Instant.toStr() = this.toLocalDateTime(TimeZone.currentSystemDefault()).format(customFormat)


fun <T> myLazy(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, initializer)


fun Long.formatAsFileSize(): String {
    val size = this
    val gb = size * 100 / (1024 * 1024 * 1024)
    if (gb.absoluteValue >= 10) {
        return "" + (gb / 100.0) + " GB"
    }
    val mb = size * 10 / (1024 * 1024)
    if (mb.absoluteValue >= 10) {
        return "" + (mb / 10.0) + " MB"
    }
    val kb = size * 10 / 1024
    if (kb.absoluteValue >= 10) {
        return "" + (kb / 10.0) + " KB"
    }
    return "$size Byte"
}


fun isTest(): Boolean {
    return Exception().stackTrace.any { it.className.contains(".junit.") }
}