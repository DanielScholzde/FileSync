package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.persistence.File2
import java.util.*


class EqualsAndHashCodeSupplierImpl(private val mode: EnumSet<MatchMode>) : EqualsAndHashCodeSupplier<File2> {

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
        var result = 1
        if (MatchMode.HASH in mode) {
            result = 31 * result + (obj.hash?.hash?.hashCode() ?: 0)
        }
        if (MatchMode.PATH in mode) {
            result = 31 * result + obj.folderId.hashCode() // TODO case sensitive?
        }
        if (MatchMode.FILENAME in mode) {
            result = 31 * result + obj.name.hashCode()
        }
        if (MatchMode.MODIFIED in mode) {
            result = 31 * result + obj.modified.hashCode()
        }
        return result
    }
}