package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException

class IntParam : ParamParserBase<Int, Int?>() {

    override var callback: ((Int) -> Unit)? = null
    private var value: Int? = null

    override fun matches(rawValue: String): Boolean {
        return rawValue.matches(Regex("[+-]?[0-9]+"))
    }

    override fun assign(rawValue: String) {
        value = Integer.parseInt(rawValue)
    }

    override fun exec() {
        callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun printout(): String {
        return "integer"
    }
}