package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.actions.sync.Addition
import de.danielscholz.fileSync.actions.sync.ContentChanged
import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


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

fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)


@JvmName("fileSize1")
fun Collection<FileEntity>.fileSize() = sumOf { it.size }

@JvmName("fileSize2")
fun Collection<ContentChanged>.fileSize() = sumOf { it.to.size }

@JvmName("fileSize3")
fun Collection<Addition>.fileSize() = sumOf { it.to.size }