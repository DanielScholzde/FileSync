package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.common.fs.*
import java.io.File
import java.nio.file.Files
import kotlin.time.measureTime


fun main() {

//    val flow = flow {
//        emit(1)
//        emit(2)
//        emit(3)
//        emit(4)
//    }
//    flow.tee({ collect { println("1: "+it+" "+System.nanoTime()) } }, { collect { println("2: "+it+" "+System.nanoTime()) } })
//    println()
//    flow.tee({ collect { println("1: "+it) } }, { collect { println("2: "+it) } })
//
//return@runBlocking Unit

    val password = "test"
    val file = "Test_copy.mpg"

    // warmup
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
//    readFile(File(file)).encryptToFile(File("$file.encrypt"), password)

    measureTime {
        readFile(File(file)).encryptToFile(File("$file.encrypt"), password)
    }.let { println(it) }

    if (File("$file.encrypt").length() != File(file).length() + AES_FILESIZE_OVERHEAD) throw Exception("Size")

    decryptFileToSeq(File("$file.encrypt"), password).writeToFile(File("$file.tmp"))

    if (!Files.readAllBytes(File(file).toPath()).contentEquals(Files.readAllBytes(File("$file.tmp").toPath()))) throw Exception("UNGLEICH!")

    File("$file.tmp").delete()
    File("$file.encrypt").delete()
}