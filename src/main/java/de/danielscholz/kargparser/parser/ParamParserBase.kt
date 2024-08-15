package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParser
import de.danielscholz.kargparser.ArgParserConfig
import de.danielscholz.kargparser.IValueParamParser

/**
 * @param R Lower Bound Type
 * @param S Upper Bound Type
 */
abstract class ParamParserBase<R, S> : IValueParamParser<R, S> {

    protected var argParser: ArgParser<*>? = null
    protected var config: ArgParserConfig = ArgParser.defaultConfig

    protected val intRange1to1 = 1..1

    override fun init(parentArgParser: ArgParser<*>, config: ArgParserConfig) {
        argParser = parentArgParser
        this.config = config
    }

    override fun numberOfSeparateValueArgsToAccept(): IntRange? {
        return if (config.onlyFilesAsSeperateArgs) null else intRange1to1
    }

    override fun convertToStr(value: S): String? {
        if (value is Collection<*>) {
            return if (value.isEmpty()) null else value.joinToString(", ")
        }
        return if (value != null) value.toString() else null
    }
}