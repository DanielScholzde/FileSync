package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.Global
import kotlinx.datetime.*
import kotlinx.datetime.format.char
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
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
        println("cancel -> exit..")
        throw CancelException()
    }
}

fun guardWithLockFile(lockfile: File, block: () -> Unit) {
    try {
        Files.createFile(lockfile.toPath())

        block()

        lockfile.delete()
    } catch (e: FileAlreadyExistsException) {
        println("Lockfile could not be created: ${e.message}")
    }
}



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