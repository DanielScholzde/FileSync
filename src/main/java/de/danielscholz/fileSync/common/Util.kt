package de.danielscholz.fileSync.common

import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
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
    var exception: Throwable? = null
    try {
        Files.createFile(lockfile.toPath())

        try {
            block()
        } catch (e: Throwable) {
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


fun <T> myLazy(initializer: () -> T): Lazy<T> = lazy(LazyThreadSafetyMode.NONE, initializer)

@OptIn(ExperimentalContracts::class)
inline fun <R> supply(block: () -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block()
}

@OptIn(ExperimentalContracts::class)
inline fun exec(block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    block()
}


fun Long.formatAsFileSize(): String {
    val size = this
    val gb = size * 10 / (1024 * 1024 * 1024)
    if (gb.absoluteValue >= 10) {
        return "${gb / 10.0} GB".replace('.', ',')
    }
    val mb = size * 10 / (1024 * 1024)
    if (mb.absoluteValue >= 10) {
        return "${mb / 10.0} MB".replace('.', ',')
    }
    val kb = size * 10 / 1024
    if (kb.absoluteValue >= 10) {
        return "${kb / 10.0} KB".replace('.', ',')
    }
    return "$size Byte"
}


fun isTest(): Boolean {
    return Exception().stackTrace.any { it.className.contains(".junit.") }
}