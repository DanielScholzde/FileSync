@file:Suppress("ClassName")

package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.persistence.File2


fun interface ResultFilter {

    fun filter(file1: File2, file2: File2): Boolean

}


val HASH_EQ = ResultFilter { file1, file2 ->
    file1.size == file2.size && (file1.hash?.hash == file2.hash?.hash)
}

val HASH_NEQ = not(HASH_EQ)

val MODIFIED_NEQ = ResultFilter { file1, file2 ->
    file1.modified != file2.modified
}


infix fun ResultFilter.and(other: ResultFilter) =
    ResultFilter { file1, file2 ->
        this@and.filter(file1, file2) && other.filter(file1, file2)
    }

infix fun ResultFilter.or(other: ResultFilter) =
    ResultFilter { file1, file2 ->
        this@or.filter(file1, file2) || other.filter(file1, file2)
    }

fun not(filter: ResultFilter) =
    ResultFilter { file1, file2 ->
        !filter.filter(file1, file2)
    }
