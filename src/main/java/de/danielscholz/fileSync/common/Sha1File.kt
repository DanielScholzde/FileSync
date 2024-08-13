package de.danielscholz.fileSync.common

import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*


fun computeSHA1(file: File): String = computeSHA1(FileInputStream(file))

fun computeSHA1(inputStream: FileInputStream): String {
    val digest = MessageDigest.getInstance("SHA-1")

    DigestInputStream(inputStream, digest).use { digestInputStream ->
        val buffer = ByteArray(4096)
        while (digestInputStream.read(buffer, 0, buffer.size) != -1) {
            // read file stream without buffer
            testIfCancel()
        }
        val msgDigest = digestInputStream.messageDigest
        return Base64.getEncoder().encodeToString(msgDigest.digest())
    }
}

suspend fun Flow<ByteArray>.computeSHA1(): String {
    val digest = MessageDigest.getInstance("SHA-1")
    this.collect {
        digest.update(it)
        testIfCancel()
    }
    return Base64.getEncoder().encodeToString(digest.digest()) // TODO
}