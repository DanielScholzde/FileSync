package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.persistence.File2
import java.util.*

/**
 * Calculates the union of two collections. Equal entries are taken from collection1!
 */
class Union(private val mode: EnumSet<MatchMode>, private val errorOnKeyCollision: Boolean) {

    context(FoldersContext, CaseSensitiveContext)
    fun apply(collection1: Collection<File2>, collection2: Collection<File2>): Collection<File2> {

        val resultMap = LinkedHashMap<String, File2>()

        collection1.forEach {
            val key = createKey(it, mode)
            if (resultMap.put(key, it) == null) {
                // ok
            } else if (errorOnKeyCollision) {
                throw Exception("Error: The match mode $mode creates duplicates within collection 1!")
            }
        }

        val collection2Keys = HashSet<String>()

        collection2.forEach {
            val key = createKey(it, mode)

            if (errorOnKeyCollision && !collection2Keys.add(key)) {
                throw Exception("Error: The match mode $mode creates duplicates within collection 2!")
            }

            if (!resultMap.containsKey(key)) {
                resultMap[key] = it
            }
        }

        return resultMap.values
    }

}