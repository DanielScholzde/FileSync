package de.danielscholz.fileSync.common

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch


suspend fun <T, R1, R2> Flow<T>.tee(consumer1: suspend Flow<T>.() -> R1, consumer2: suspend Flow<T>.() -> R2): Pair<R1, R2> {
    var result1: R1? = null
    var result2: R2? = null
    coroutineScope {
        val c1 = Channel<T>()
        val c2 = Channel<T>()

        launch {
            result1 = c1.consumeAsFlow().consumer1()
        }
        launch {
            result2 = c2.consumeAsFlow().consumer2()
        }
        this@tee.collect {
            //println("send to channel 1 (${Thread.currentThread().name})")
            c1.send(it)
            //println("send to channel 2 (${Thread.currentThread().name})")
            c2.send(it)
        }
        //println("tee finished (${Thread.currentThread().name})")
        c1.close()
        c2.close()
    }
    return result1!! to result2!!
}