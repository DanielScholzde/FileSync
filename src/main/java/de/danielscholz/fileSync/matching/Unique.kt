package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.common.CaseSensitiveContext
import de.danielscholz.fileSync.common.FoldersContext
import de.danielscholz.fileSync.persistence.File2
import java.util.*

/**
 * Calculates a unique collection. If errorOnKeyCollision = false and there are more than one entry with the calculated ID only the first entry is returned.
 */
class Unique(private val mode: EnumSet<MatchMode>, private val errorOnKeyCollision: Boolean) {

    context(FoldersContext, CaseSensitiveContext)
    fun apply(collection: Collection<File2>): Collection<File2> {

        val map = LinkedHashMap<String, File2>()

        collection.forEach {
            val key = createKey(it, mode)
            if (!map.containsKey(key)) {
                map[key] = it
            } else if (errorOnKeyCollision) {
                throw Exception("Error: The match mode $mode creates duplicates!")
            }
        }

        return map.values
    }

}