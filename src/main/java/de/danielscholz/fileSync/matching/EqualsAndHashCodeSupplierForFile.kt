package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.actions.Folders
import de.danielscholz.fileSync.persistence.FileEntity
import java.util.*


class EqualsAndHashCodeSupplierForFile(private val mode: EnumSet<MatchMode>, private val folders: Folders, private val isCaseSensitive: Boolean) :
    EqualsAndHashCodeSupplier<FileEntity> {

    override fun equals(obj1: FileEntity, obj2: FileEntity): Boolean {
        if (MatchMode.HASH in mode) {
            if (obj1.size != obj2.size || obj1.hash?.hash != obj2.hash?.hash) return false
        }

        var compFilename = MatchMode.FILENAME in mode

        // Workaround for empty files (which do not have a hash): compare filename and path too!
        if (obj1.hash == null && obj2.hash == null && MatchMode.HASH in mode) {
            compFilename = true
        }

        if (MatchMode.PATH in mode) {
            if (isCaseSensitive) {
                if (obj1.folderId != obj2.folderId) return false
            } else {
                if (folders.getFullPathLowercase(obj1.folderId) != folders.getFullPathLowercase(obj2.folderId)) return false
            }
        }
        if (compFilename) {
            if (obj1.name != obj2.name) return false
        }
        if (MatchMode.MODIFIED in mode) {
            if (obj1.modified != obj2.modified) return false
        }
        return true
    }

    override fun hashCode(obj: FileEntity): Int {
        var result = 1
        if (MatchMode.HASH in mode) {
            result = 31 * result + (obj.hash?.hash?.hashCode() ?: 0)
        }
        if (MatchMode.PATH in mode) {
            result = 31 * result + folders.getFullPathLowercase(obj.folderId).hashCode()
        }
        if (MatchMode.FILENAME in mode) {
            result = 31 * result + obj.nameLowercase.hashCode()
        }
        if (MatchMode.MODIFIED in mode) {
            result = 31 * result + obj.modified.hashCode()
        }
        return result
    }
}