package de.danielscholz.fileSync.common

import java.io.File
import java.io.InputStream
import kotlin.math.min

class ChecksumCreator(
    private val inputStream: InputStream,
    private val fileSize: Long,
    private val file: File? = null,
    private val byteArray: ByteArray? = null
) {

    data class Checksum(
        val sha1: String,
        val sha1ChunksFromBeginning: List<String>,
        val sha1ChunksFromEnd: List<String>
    )

    private val chunkCreator = ChunkCreator(fileSize)
    private val buffer = ByteArray(512 * 1024)
    private var finish = false
    private val maxFirstIterations = 1024 * 1024 / buffer.size // = 128; d.h. 1 MB
    private var byteArrayPos = 0

    init {
        chunkCreator.init()
        calc(true)
    }

    private fun calc(onlyFirstIterations: Boolean) {
        if (finish) return
        try {
            var i = 0
            while (true) {
                val n = inputStream.read(buffer)
                if (n > 0) {
                    chunkCreator.update(buffer, n)
                    i++
                    if (byteArray != null && byteArrayPos < byteArray.lastIndex) {
                        buffer.copyInto(byteArray, byteArrayPos, 0, min(n, byteArray.lastIndex - byteArrayPos))
                        byteArrayPos += n
                    }
                    if (onlyFirstIterations && i >= maxFirstIterations) return
                } else {
                    finish = true
                    break
                }
                testIfCancel()
            }
        } catch (e: Exception) {
            if (e !is CancelException && file != null && fileSize != file.length()) {
                //Global.stat.failedFileReads.add("ERROR: $file: Hash could not be calculated. ${e.javaClass.simpleName}: ${e.message}")
            } else throw e
        }
    }

    fun getChecksumFromBeginTemp(): List<String> {
        val sha1ChunksFromBegin = chunkCreator.sha1ChunksFromBegin
        if (sha1ChunksFromBegin.isEmpty()) {
            if (fileSize < ChunkCreator.minimumChunkSize * 2) {
                val checksum = calcChecksum()
                return listOf(checksum.sha1.getSha1Chunk())
            }
            throw IllegalStateException()
        }
        return sha1ChunksFromBegin
    }

    fun calcChecksum(): Checksum {
//      if (!Config.INST.createHashOnlyForFirstMb) {
        calc(false)
//      }

        var sha1ChunksFromBegin: List<String> = chunkCreator.sha1ChunksFromBegin
        var sha1ChunksFromEnd: List<String> = chunkCreator.sha1ChunksFromEnd
        if (sha1ChunksFromBegin.isEmpty()) {
            sha1ChunksFromBegin = listOf(chunkCreator.sha1!!.getSha1Chunk())
            if (sha1ChunksFromEnd.isNotEmpty()) throw IllegalStateException()
            sha1ChunksFromEnd = sha1ChunksFromBegin
        }
        if (sha1ChunksFromEnd.isEmpty()) {
            throw IllegalStateException()
        }
        if (chunkCreator.sha1 == null) { // this is the case when file size changed during read operation
            throw FileSizeChangedException()
        }
        return Checksum(chunkCreator.sha1!!, sha1ChunksFromBegin, sha1ChunksFromEnd)
    }
}