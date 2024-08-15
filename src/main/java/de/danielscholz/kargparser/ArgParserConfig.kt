package de.danielscholz.kargparser

class ArgParserConfig(
    var ignoreCase: Boolean = false,
    var prefixStr: String = "--",
    var noPrefixForActionParams: Boolean = false,
    var onlyFilesAsSeperateArgs: Boolean = false
)