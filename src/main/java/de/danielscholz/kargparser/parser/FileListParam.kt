package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException
import java.io.File

class FileListParam(
    private val numberOfFilesToAccept: IntRange = 1..Int.MAX_VALUE,
    private val checkIsDir: Boolean = false,
    private val checkIsFile: Boolean = false
) : ParamParserBase<MutableList<File>, Collection<File>?>() {

    override var callback: ((MutableList<File>) -> Unit)? = null
    private var value: MutableList<File> = mutableListOf()

    override fun numberOfSeparateValueArgsToAccept(): IntRange? {
        return numberOfFilesToAccept
    }

    override fun matches(rawValue: String): Boolean {
        return rawValue != ""
    }

    override fun assign(rawValue: String) {
        val file = File(rawValue)
        if (checkIsFile && !file.isFile) throw ArgParseException("$file is no file!", argParser!!)
        if (checkIsDir && !file.isDirectory) throw ArgParseException("$file is no directory!", argParser!!)
        value.add(file)
    }

    override fun exec() {
        callback?.invoke(value) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun printout(): String {
        return "file1 file2 .."
    }
}