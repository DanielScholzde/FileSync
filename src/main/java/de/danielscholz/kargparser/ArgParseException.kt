package de.danielscholz.kargparser

class ArgParseException(message: String, internal val source: ArgParser<*>) : RuntimeException(message)