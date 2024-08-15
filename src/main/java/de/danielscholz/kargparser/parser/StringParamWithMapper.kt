package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException

/**
 * @param R Lower Bound Type
 * @param S Upper Bound Type
 */
class StringParamWithMapper<R, S>(
    private val matcher: Regex = Regex(".*"),
    private val mapper: (String) -> R,
    private val toStringMapper: (S) -> String?,
    private val typeDescription: String? = null
) : ParamParserBase<R, S>() {

    override var callback: ((R) -> Unit)? = null
    private var value: R? = null

    override fun matches(rawValue: String): Boolean {
        return matcher.matches(rawValue)
    }

    override fun assign(rawValue: String) {
        value = mapper(rawValue)
    }

    override fun exec() {
        callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun convertToStr(value: S): String? {
        return toStringMapper(value)
    }

    override fun printout(): String {
        return typeDescription ?: "value"
    }
}