package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.persistence.File2


fun Collection<Pair<File2, File2>>.leftSide() = this.map { it.first }
fun Collection<Pair<File2, File2>>.rightSide() = this.map { it.second }

fun Collection<Change>.from() = this.map { it.from }
fun Collection<Change>.to() = this.map { it.to }