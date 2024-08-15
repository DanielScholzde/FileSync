package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException

class IntRangeParam : ParamParserBase<IntRange, IntRange?>() {

    override var callback: ((IntRange) -> Unit)? = null
    private var value: IntRange? = null

    override fun matches(rawValue: String): Boolean {
        return rawValue.matches(Regex("[+-]?[0-9]+-[+-]?[0-9]+"))
    }

    override fun assign(rawValue: String) {
        val split = rawValue.split("-")
        value = split[0].toInt()..split[1].toInt()
    }

    override fun exec() {
        callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun convertToStr(value: IntRange?): String? {
        return if (value != null) "${value.first}-${value.last}" else null
    }

    override fun printout(): String {
        return "integer-integer"
    }
}