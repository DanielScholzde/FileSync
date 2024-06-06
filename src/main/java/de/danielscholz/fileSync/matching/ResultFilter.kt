@file:Suppress("ClassName")

package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.persistence.File2


typealias ResultFilter = (Pair<File2, File2>) -> Boolean


val HASH_EQ: ResultFilter = { (file1, file2) ->
    file1.size == file2.size && (file1.hash?.hash == file2.hash?.hash)
}

val HASH_NEQ: ResultFilter = { pair ->
    !HASH_EQ(pair)
}

val MODIFIED_NEQ: ResultFilter = { (file1, file2) ->
    file1.modified != file2.modified
}


infix fun ResultFilter.and(other: ResultFilter): ResultFilter = { pair ->
    this@and(pair) && other(pair)
}

infix fun ResultFilter.or(other: ResultFilter): ResultFilter = { pair ->
    this@or(pair) || other(pair)
}

fun not(filter: ResultFilter): ResultFilter = { pair ->
    !filter(pair)
}
