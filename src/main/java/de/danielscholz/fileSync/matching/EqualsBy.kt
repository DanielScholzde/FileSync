package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.mutableListMultimapOf


class EqualsBy<T : Any>(private val ignoreDuplicatesOnIntersect: Boolean, private val equalsAndHashcodeSupplier: EqualsAndHashCodeSupplier<T>) {

    infix fun Collection<T>.intersect(collection2: Collection<T>): Collection<Pair<T, T>> {
        val collection1 = this

        if (collection1.isEmpty() || collection2.isEmpty()) {
            return listOf()
        }

        val result = mutableListOf<Pair<T, T>>()

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
                if (set1.size > 1 || set2.size > 1) {
                    return@forEach
                }
                result.add(set1[0] to set2[0])
            }

            return result
        }

        val collection1AsMap = HashMap<EqualsDelegate<T>, T>()

        collection1.forEach {
            if (collection1AsMap.put(EqualsDelegate(it, equalsAndHashcodeSupplier), it) != null) {
                throw Exception("Error: The match mode creates duplicates within collection 1!")
            }
        }

        val set2 = HashSet<EqualsDelegate<T>>()

        collection2.forEach { e2 ->
            val equalsDelegate2 = EqualsDelegate(e2, equalsAndHashcodeSupplier)
            if (!set2.add(equalsDelegate2)) {
                throw Exception("Error: The match mode creates duplicates within collection 2!")
            }
            val e1 = collection1AsMap[equalsDelegate2]
            if (e1 != null) {
                result.add(Pair(e1, e2))
            }
        }

        return result
    }

    operator fun Collection<T>.minus(other: Collection<T>): Collection<T> {

        if (this.isEmpty()) return listOf()
        if (other.isEmpty()) return this

        val set1 = HashSet<EqualsDelegate<T>>()

        this.forEach {
            if (!set1.add(EqualsDelegate(it, equalsAndHashcodeSupplier))) {
                throw Exception("Error: The match mode creates duplicates within collection 1!")
            }
        }

        val set2 = HashSet<EqualsDelegate<T>>()

        other.forEach {
            val equalsDelegate = EqualsDelegate(it, equalsAndHashcodeSupplier)
            if (!set2.add(equalsDelegate)) {
                throw Exception("Error: The match mode creates duplicates within collection 2!")
            }
            set1.remove(equalsDelegate)
        }

        return set1.map { it.obj }
    }
}