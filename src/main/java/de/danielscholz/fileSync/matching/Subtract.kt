package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.persistence.File2
import java.util.*

/**
 * Calculates collection1 minus collection2
 */
class Subtract(private val mode: EnumSet<MatchMode>) {

    context(FoldersContext, CaseSensitiveContext)
    fun apply(collection1: Collection<File2>, collection2: Collection<File2>): Collection<File2> {

        if (collection1.isEmpty()) return listOf()
        if (collection2.isEmpty()) return collection1

        val collection1AsMap = HashMap<String, File2>()

        collection1.forEach {
            if (collection1AsMap.put(createKey(it, mode), it) != null) {
                throw Exception("Error: The match mode $mode creates duplicates within collection 1!")
            }
        }

        val collection2Keys = mutableSetOf<String>()

        collection2.forEach {
            val key = createKey(it, mode)
            if (!collection2Keys.add(key)) {
                throw Exception("Error: The match mode $mode creates duplicates within collection 2!")
            }
            collection1AsMap.remove(key)
        }

        return collection1AsMap.values
    }

}
