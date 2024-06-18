package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.mutableListMultimapOf


fun <T : Any, R> equalsBy(equalsAndHashcodeSupplier: EqualsAndHashCodeSupplier<T>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<T>.() -> R): R {

    val equalsBy = EqualsBy(ignoreDuplicatesOnIntersect, equalsAndHashcodeSupplier)

    return equalsBy.block()
}

class EqualsBy<T : Any>(private val ignoreDuplicatesOnIntersect: Boolean, private val equalsAndHashcodeSupplier: EqualsAndHashCodeSupplier<T>) {

    infix fun T.eq(other: T): Boolean {
        return equalsAndHashcodeSupplier.equals(this, other)
    }

    infix fun Collection<T>.intersect(collection2: Collection<T>): Collection<IntersectResult<T>> {
        val collection1 = this

        if (collection1.isEmpty() || collection2.isEmpty()) {
            return listOf()
        }

        val result = mutableListOf<IntersectResult<T>>()

        if (ignoreDuplicatesOnIntersect) {
            val collection1AsMultimap = mutableListMultimapOf<EqualsDelegate<T>, T>()
            val collection2AsMultimap = mutableListMultimapOf<EqualsDelegate<T>, T>()

            collection1.forEach {
                collection1AsMultimap.put(EqualsDelegate(it, equalsAndHashcodeSupplier), it)
            }
            collection2.forEach {
                collection2AsMultimap.put(EqualsDelegate(it, equalsAndHashcodeSupplier), it)
            }

            collection2AsMultimap.keySet().forEach { key ->
                val set1 = collection1AsMultimap[key]
                val set2 = collection2AsMultimap[key]
                if (set1.size > 1 || set2.size > 1 || set1.size == 0) {
                    return@forEach
                }
                result.add(IntersectResult(set1[0], set2[0]))
            }

            return result
        }

        val collection1AsMap = HashMap<EqualsDelegate<T>, T>()

        collection1.forEach {
            if (collection1AsMap.put(EqualsDelegate(it, equalsAndHashcodeSupplier), it) != null) {
                throw Exception("Error: The match mode creates duplicates within collection 1 ($it)")
            }
        }

        val set2 = HashSet<EqualsDelegate<T>>()

        collection2.forEach { entry2 ->
            val equalsDelegate2 = EqualsDelegate(entry2, equalsAndHashcodeSupplier)
            if (!set2.add(equalsDelegate2)) {
                throw Exception("Error: The match mode creates duplicates within collection 2 ($entry2)")
            }
            val entry1 = collection1AsMap[equalsDelegate2]
            if (entry1 != null) {
                result.add(IntersectResult(entry1, entry2))
            }
        }

        return result
    }

    operator fun Collection<T>.plus(collection2: Collection<T>): List<T> {
        val collection1 = this

        val result = mutableSetOf<EqualsDelegate<T>>()

        collection1.forEach {
            if (!result.add(EqualsDelegate(it, equalsAndHashcodeSupplier))) {
                throw Exception("Error: The match mode creates duplicates within collection 1 ($it)")
            }
        }

        collection2.forEach {
            result.add(EqualsDelegate(it, equalsAndHashcodeSupplier))
        }

        return result.map { it.obj }
    }

    operator fun Collection<T>.minus(other: Collection<T>): Collection<T> {

        if (this.isEmpty()) return listOf()
        if (other.isEmpty()) return this

        val set1 = HashSet<EqualsDelegate<T>>()

        this.forEach {
            val element = EqualsDelegate(it, equalsAndHashcodeSupplier)
            if (!set1.add(element)) {
                throw Exception("Error: The match mode creates duplicates within collection 1 ($it)")
            }
        }

        val set2 = HashSet<EqualsDelegate<T>>()

        other.forEach {
            val equalsDelegate = EqualsDelegate(it, equalsAndHashcodeSupplier)
            if (!set2.add(equalsDelegate)) {
                throw Exception("Error: The match mode creates duplicates within collection 2 ($it)")
            }
            set1.remove(equalsDelegate)
        }

        return set1.map { it.obj }
    }
}