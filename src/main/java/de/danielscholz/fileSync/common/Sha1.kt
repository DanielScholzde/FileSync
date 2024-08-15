package de.danielscholz.fileSync.common

import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest


suspend fun Flow<ByteArray>.computeSHA1(): String {
    val digest = MessageDigest.getInstance("SHA-1")
    this.collect { data ->
        //println("update digest (${Thread.currentThread().name})")
        //println("computeSHA1.collect:   ${data.size}  ${data.sum()}  ${Thread.currentThread().name} ${digest.hashCode()}")
        digest.update(data)
        testIfCancel()
    }
    //println("sha1 finished")
    val sha1 = digest.digest().toBase64()
    //println("SHA-1 of flow: $sha1")
    return sha1
}