package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException
import java.time.LocalDate

/**
 * Retrieves a parameter value of format yyyy-mm-dd as a LocalDate
 */
class DateParam : ParamParserBase<LocalDate, LocalDate?>() {

    override var callback: ((LocalDate) -> Unit)? = null
    private var value: LocalDate? = null

    override fun matches(rawValue: String): Boolean {
        return true
    }

    override fun assign(rawValue: String) {
        val localDate = LocalDate.parse(rawValue)
        value = localDate
    }

    override fun exec() {
        callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun printout(): String {
        return "yyyy-mm-dd"
    }
}