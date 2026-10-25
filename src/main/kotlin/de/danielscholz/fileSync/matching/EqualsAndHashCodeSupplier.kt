package de.danielscholz.fileSync.matching


interface EqualsAndHashCodeSupplier<T> {
    fun equals(obj1: T, obj2: T): Boolean
    fun hashCode(obj: T): Int
}