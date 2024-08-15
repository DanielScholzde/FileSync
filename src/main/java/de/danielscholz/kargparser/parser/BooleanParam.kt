package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException

class BooleanParam(
    acceptedValues: Set<String> = setOf("yes", "no"),
    acceptedValuesWithMeaningTrue: Set<String> = setOf("yes"),
    additionalAcceptedValues: Set<String> = setOf(),
    additionalAcceptedValuesWithMeaningTrue: Set<String> = setOf(),
    private val defaultValue: Boolean = true
) : ParamParserBase<Boolean, Boolean?>() {

    private val allValues = acceptedValues + additionalAcceptedValues
    private val allValuesLowercase = allValues.map { it.lowercase() }
    private val trueValues = acceptedValuesWithMeaningTrue + additionalAcceptedValuesWithMeaningTrue
    private val trueValuesLowercase = trueValues.map { it.lowercase() }

    override var callback: ((Boolean) -> Unit)? = null
    private var value: Boolean? = null

    init {
        if (!acceptedValues.containsAll(acceptedValuesWithMeaningTrue)) {
            throw Exception("acceptedValues does not contain all values from acceptedValuesWithMeaningTrue!")
        }
    }

    override fun numberOfSeparateValueArgsToAccept(): IntRange? {
        return null
    }

    override fun matches(rawValue: String): Boolean {
        return rawValue == "" || rawValue.lowercase() in allValuesLowercase
    }

    override fun assign(rawValue: String) {
        if (rawValue != "") {
            value = rawValue.lowercase() in trueValuesLowercase
        }
    }

    override fun exec() {
        callback?.invoke(value ?: defaultValue) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun convertToStr(value: Boolean?): String? {
        return if (value == true) trueValues.maxByOrNull { it.length } else if (value == false) (allValues - trueValues).maxByOrNull { it.length } else null
    }

    override fun printout(): String {
        return "[:" + allValues.joinToString("|") + "]"
    }
}