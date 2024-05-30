package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.actions.SyncFiles
import de.danielscholz.fileSync.persistence.File2


fun String.getSha1Chunk(): String {
    return substring(0, 12)
}


fun String.ensureSuffix(suffix: String): String {
    return if (this.endsWith(suffix)) this else this + suffix
}

fun String.ensurePrefix(prefix: String): String {
    return if (this.startsWith(prefix)) this else prefix + this
}

fun String.getFileExtension(): String? {
    val ext = substringAfterLast('.', "")
    return if (ext.isNotBlank()) ext else null
}

fun leftPad(num: Int, length: Int): String {
    return num.toString().padStart(length)
}


@JvmName("fileSize1")
fun Collection<File2>.fileSize() = sumOf { it.size }

@JvmName("fileSize2")
fun Collection<SyncFiles.ContentChanged>.fileSize() = sumOf { it.to.size }
