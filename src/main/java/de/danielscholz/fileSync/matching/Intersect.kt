package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.common.mutableListMultimapOf
import de.danielscholz.fileSync.persistence.File2
import kotlin.math.min

/**
 * Calculates the intersection of two collections.
 */
class Intersect(private val keySupplier: KeySupplier, private val ignoreDuplicates: Boolean = false) {

    context(FoldersContext, CaseSensitiveContext)
    fun apply(collection1: Collection<File2>, collection2: Collection<File2>): List<Pair<File2, File2>> {

        if (collection1.isEmpty() || collection2.isEmpty()) {
            return listOf()
        }

        val result = mutableListOf<Pair<File2, File2>>()
        if (result is ArrayList) {
            result.ensureCapacity(min(collection1.size, collection2.size) / 10)
        }

        if (ignoreDuplicates) {
            val collection1AsMultimap = mutableListMultimapOf<String, File2>()
            val collection2AsMultimap = mutableListMultimapOf<String, File2>()

            collection1.forEach {
                collection1AsMultimap.put(keySupplier.getKey(it), it)
            }
            collection2.forEach {
                collection2AsMultimap.put(keySupplier.getKey(it), it)
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

        val collection1AsMap = HashMap<String, File2>()

        collection1.forEach {
            if (collection1AsMap.put(keySupplier.getKey(it), it) != null) {
                throw Exception("Error: The match mode creates duplicates within collection 1!")
            }
        }

        val collection2Keys = mutableSetOf<String>()

        collection2.forEach {
            val key = keySupplier.getKey(it)
            if (!collection2Keys.add(key)) {
                throw Exception("Error: The match mode creates duplicates within collection 2!")
            }
            val fileLocation = collection1AsMap[key]
            if (fileLocation != null) {
                result.add(Pair(fileLocation, it))
            }
        }

        return result
    }

}