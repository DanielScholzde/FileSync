package de.danielscholz.fileSync.common

import kotlinx.datetime.*
import kotlinx.datetime.format.char
import java.io.File
import java.io.IOException
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
    var exception: Exception? = null
    try {
        Files.createFile(lockfile.toPath())

        try {
            block()
        } catch (e: Exception) {
            exception = e
        }

        lockfile.delete()
    } catch (e: FileAlreadyExistsException) {
        println("Sync can not be started, because there is already an ongoing sync process. If not, you should delete the file $lockfile")
    } catch (e: IOException) {
        println("Lockfile could not be created: ${e.message}")
    }
    if (exception != null) {
        throw exception
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