package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException
import java.time.LocalTime

/**
 * Retrieves a parameter value of format hh:mm:ss as a LocalTime
 */
class TimeParam : ParamParserBase<LocalTime, LocalTime?>() {

    override var callback: ((LocalTime) -> Unit)? = null
    private var value: LocalTime? = null

    override fun matches(rawValue: String): Boolean {
        return true
    }

    override fun assign(rawValue: String) {
        val localDate = LocalTime.parse(rawValue)
        value = localDate
    }

    override fun exec() {
        callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun printout(): String {
        return "hh:mm:ss"
    }
}