package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException

class StringSetParam(
    private val numberOfValuesToAccept: IntRange = 1..Int.MAX_VALUE,
    private val typeDescription: String? = null,
    private val regex: Regex? = null,
    private val mapper: ((String) -> String)? = null
) : ParamParserBase<MutableSet<String>, Collection<String>?>() {

    override var callback: ((MutableSet<String>) -> Unit)? = null
    private var valueSet: MutableSet<String> = mutableSetOf()

    override fun numberOfSeparateValueArgsToAccept(): IntRange? {
        return numberOfValuesToAccept
    }

    override fun matches(rawValue: String): Boolean {
        return rawValue != "" && (regex == null || rawValue.matches(regex))
    }

    override fun assign(rawValue: String) {
        var str = rawValue
        if (mapper != null) {
            str = mapper.invoke(str)
        }
        valueSet.add(str)
    }

    override fun exec() {
        callback?.invoke(valueSet) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun printout(): String {
        return typeDescription ?: "value1 value2 .."
    }
}