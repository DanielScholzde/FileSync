package de.danielscholz.fileSync.common

import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*


fun computeSHA1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    DigestInputStream(FileInputStream(file), digest).use { digestInputStream ->
        val buffer = ByteArray(4096)
        while (digestInputStream.read(buffer, 0, buffer.size) != -1) {
            // read file stream without buffer
        }
        val msgDigest = digestInputStream.messageDigest
        return Base64.getEncoder().encodeToString(msgDigest.digest())
    }
}

