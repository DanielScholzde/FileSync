package de.danielscholz.fileSync.common

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.HashMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.SetMultimap


fun <K, V> mutableSetMultimapOf(): SetMultimap<K, V> = HashMultimap.create<K, V>()
fun <K, V> mutableListMultimapOf(): ListMultimap<K, V> = ArrayListMultimap.create<K, V>()

operator fun <K, V> ListMultimap<K, V>.set(key: K, value: V) {
    put(key, value)
}

operator fun <K, V> SetMultimap<K, V>.set(key: K, value: V) {
    put(key, value)
}


inline fun <E> Collection<E>.ifNotEmpty(block: (Collection<E>) -> Unit) {
    if (isNotEmpty()) {
        block(this)
    }
}

fun <E, T> Collection<E>.multiAssociateBy(keyExtractor: (E) -> T): ListMultimap<T, E> {
    val multimap = mutableListMultimapOf<T, E>()
    this.forEach { multimap.put(keyExtractor(it), it) }
    return multimap
}


fun <E> MutableSet<E>.replace(element: E) {
    this.remove(element) || throw IllegalStateException()
    this.add(element) || throw IllegalStateException()
}

fun <E> MutableSet<E>.addWithCheck(element: E) {
    this.add(element) || throw IllegalStateException()
}

fun <E> MutableSet<E>.removeWithCheck(element: E) {
    this.remove(element) || throw IllegalStateException()
}


/**
 * Returns a list containing all elements of the original collection and then all elements of the given [elements] collection.
 */
operator fun <T> List<T>.plus(elements: List<T>): List<T> {
    if (this.isEmpty()) return elements
    if (elements.isEmpty()) return this
    val result = ArrayList<T>(this.size + elements.size)
    result.addAll(this)
    result.addAll(elements)
    return result
}
