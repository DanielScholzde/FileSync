package de.danielscholz.fileSync.matching


class EqualsDelegate<T : Any>(val obj: T, private val equalsAndHashcodeSupplier: EqualsAndHashCodeSupplier<T>) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EqualsDelegate<*>) return false
        //if (obj::class != other.obj::class) return false
        @Suppress("UNCHECKED_CAST")
        return equalsAndHashcodeSupplier.equals(obj, other.obj as T)
    }

    override fun hashCode() = equalsAndHashcodeSupplier.hashCode(obj)
}