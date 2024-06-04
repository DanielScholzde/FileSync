package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.persistence.File2
import java.util.*


fun <R> equalsBy(mode: EnumSet<MatchMode>, ignoreDuplicatesOnIntersect: Boolean = false, block: EqualsBy<File2>.() -> R): R {

    val equalsBy = EqualsBy(ignoreDuplicatesOnIntersect, object : EqualsAndHashCodeSupplier<File2> {
        override fun equals(obj1: File2, obj2: File2): Boolean {
            if (MatchMode.HASH in mode) {
                if (obj1.size != obj2.size || obj1.hash?.hash != obj2.hash?.hash) return false
            }

            // Workaround for empty files (which do not have a hash)
            if (obj1.hash == null && obj2.hash == null && MatchMode.HASH in mode && MatchMode.FILENAME !in mode) {
                if (obj1.name != obj2.name) return false
            }
            if (obj1.hash == null && obj2.hash == null && MatchMode.HASH in mode && MatchMode.PATH !in mode) {
                if (obj1.folderId != obj2.folderId) return false
            }

            if (MatchMode.PATH in mode) {
                if (obj1.folderId != obj2.folderId) return false // TODO case sensitive?
            }
            if (MatchMode.FILENAME in mode) {
                if (obj1.name != obj2.name) return false
            }
            if (MatchMode.MODIFIED in mode) {
                if (obj1.modified != obj2.modified) return false
            }
            return true
        }

        override fun hashCode(obj: File2): Int {
            var res = 1
            if (MatchMode.HASH in mode) {
                res = 31 * res + (obj.hash?.hash?.hashCode() ?: 0)
            }
            if (MatchMode.PATH in mode) {
                res = 31 * res + obj.folderId.hashCode() // TODO case sensitive?
            }
            if (MatchMode.FILENAME in mode) {
                res = 31 * res + obj.name.hashCode()
            }
            if (MatchMode.MODIFIED in mode) {
                res = 31 * res + obj.modified.hashCode()
            }
            return res
        }
    })

    return equalsBy.block()
}