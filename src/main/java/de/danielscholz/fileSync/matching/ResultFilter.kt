@file:Suppress("ClassName")

package de.danielscholz.fileSync.matching

import de.danielscholz.fileSync.persistence.File2


typealias ResultFilter = (IntersectResult<File2>) -> Boolean


val HASH_EQ: ResultFilter = { (file1, file2) ->
    file1.size == file2.size && (file1.hash?.hash == file2.hash?.hash)
}

val HASH_NEQ: ResultFilter = { intersectResult ->
    !HASH_EQ(intersectResult)
}

val MODIFIED_NEQ: ResultFilter = { (file1, file2) ->
    file1.modified != file2.modified
}


infix fun ResultFilter.and(other: ResultFilter): ResultFilter = { intersectResult ->
    this@and(intersectResult) && other(intersectResult)
}

infix fun ResultFilter.or(other: ResultFilter): ResultFilter = { intersectResult ->
    this@or(intersectResult) || other(intersectResult)
}

fun not(filter: ResultFilter): ResultFilter = { intersectResult ->
    !filter(intersectResult)
}
