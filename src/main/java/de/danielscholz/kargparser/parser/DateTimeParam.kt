package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException
import java.time.LocalDateTime

/**
 * Retrieves a parameter value of format yyyy-mm-ddThh:mm:ss as a LocalDateTime
 */
class DateTimeParam : ParamParserBase<LocalDateTime, LocalDateTime?>() {

    override var callback: ((LocalDateTime) -> Unit)? = null
    private var value: LocalDateTime? = null

    override fun matches(rawValue: String): Boolean {
        return true
    }

    override fun assign(rawValue: String) {
        val localDate = LocalDateTime.parse(rawValue)
        value = localDate
    }

    override fun exec() {
        callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun printout(): String {
        return "yyyy-mm-ddThh:mm:ss"
    }
}