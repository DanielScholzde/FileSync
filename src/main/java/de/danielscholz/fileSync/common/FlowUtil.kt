package de.danielscholz.fileSync.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


//suspend fun <T, R1, R2> Flow<T>.tee(consumer1: suspend Flow<T>.() -> R1, consumer2: suspend Flow<T>.() -> R2): Pair<R1, R2> {
//    var result1: R1? = null
//    var result2: R2? = null
//    coroutineScope {
//        val c1 = Channel<T>()
//        val c2 = Channel<T>()
//
//        launch {
//            result1 = c1.consumeAsFlow().consumer1()
//        }
//        launch {
//            result2 = c2.consumeAsFlow().consumer2()
//        }
//        this@tee.collect {
//            println("send to channel 1 (${Thread.currentThread().name})"+" "+System.nanoTime())
//            c1.send(it)
//            println("send to channel 2 (${Thread.currentThread().name})"+" "+System.nanoTime())
//            c2.send(it)
//        }
//        println("tee finished (${Thread.currentThread().name})"+" "+System.nanoTime())
//        c1.close()
//        c2.close()
//    }
//    return result1!! to result2!!
//}

//suspend fun <T, R1, R2> Flow<T>.tee2(consumer1: suspend Flow<T>.() -> R1, consumer2: suspend Flow<T>.() -> R2): Pair<R1, R2> {
//    var result1: R1? = null
//    var result2: R2? = null
//    coroutineScope {
//     val flow = MutableSharedFlow<T>(100)
//
//        launch {
//            result1 = flow.consumer1()
//        }
//        launch {
//            result2 = flow.consumer2()
//        }
//        this@tee2.collect {
//            println("send to channel 1 (${Thread.currentThread().name})"+" "+System.nanoTime())
//            flow.emit(it)
////            println("send to channel 2 (${Thread.currentThread().name})"+" "+System.nanoTime())
////            c2.send(it)
//        }
//
//        println("tee finished (${Thread.currentThread().name})"+" "+System.nanoTime())
////        c1.close()
////        c2.close()
//    }
//    return result1!! to result2!!
//}


fun readFile(file: File): Flow<ByteArray> = flow {
    FileInputStream(file).use { inputStream ->
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } > 0) {
            if (buffer.size == bytesRead) {
                //println("emit1")
                emit(buffer) // Attention: no copy!!
            } else {
                //println("emit2")
                emit(buffer.copyOf(bytesRead))
            }
        }
    }
}

suspend fun Flow<ByteArray>.writeToFile(file: File) {
    FileOutputStream(file).use { outputStream ->
        this.collect { data ->
            outputStream.write(data)
        }
    }
}