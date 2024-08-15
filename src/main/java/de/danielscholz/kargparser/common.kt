package de.danielscholz.kargparser

inline fun <R> List<R>.ifNotEmpty(callback: List<R>.() -> Unit) {
    if (this.isNotEmpty()) callback()
}

/**
 * Returns `true` if at least one element matches the given [predicate].
 */
inline fun <T> Iterable<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return false
    var i = 0
    for (element in this) {
        if (predicate(i, element)) {
            return true
        }
        i++
    }
    return false
}