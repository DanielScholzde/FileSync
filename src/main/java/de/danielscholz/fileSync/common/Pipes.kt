package de.danielscholz.fileSync.common

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream


fun main() {
    val content = "I'm going through the pipe."

    val originOut = ByteArrayOutputStream()
    originOut.write(content.toByteArray())


    //connect the pipe
    val `in` = PipedInputStream()
    val out = PipedOutputStream(`in`)

    `in`.use {
        Thread.ofVirtual().start {
            try {
                out.use {
                    originOut.writeTo(out)
                }
            } catch (iox: IOException) {
                // ...
            }
        }
        val inContent = String(`in`.readAllBytes())

    }
}