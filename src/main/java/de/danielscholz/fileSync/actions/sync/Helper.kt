package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.persistence.File2


fun Collection<Pair<File2, File2>>.leftSide() = this.map { it.first }
fun Collection<Pair<File2, File2>>.rightSide() = this.map { it.second }

fun Collection<Addition>.files() = this.map { it.file }
fun Collection<Deletion>.files() = this.map { it.file }

fun <T : File2?, R : File2?> Collection<Change2<T, R>>.from() = this.map { it.from }
fun <T : File2?, R : File2?> Collection<Change2<T, R>>.to() = this.map { it.to }
