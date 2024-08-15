package de.danielscholz.kargparser

/**
 * @param R Lower Bound Type
 * @param S Upper Bound Type
 */
interface IValueParamParser<R, in S> {

    fun init(parentArgParser: ArgParser<*>, config: ArgParserConfig)

    var callback: ((R) -> Unit)?

    fun numberOfSeparateValueArgsToAccept(): IntRange?

    fun matches(rawValue: String): Boolean

    fun assign(rawValue: String)

    fun exec()

    fun printout(): String

    fun convertToStr(value: S): String?

}