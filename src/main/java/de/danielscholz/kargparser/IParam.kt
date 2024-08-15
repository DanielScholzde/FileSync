package de.danielscholz.kargparser

import de.danielscholz.kargparser.ArgParser.Argument

interface IParam {

    fun init(argParser: ArgParser<*>, config: ArgParserConfig)

    fun matches(arg: String, idx: Int, allArguments: List<Argument>): Boolean

    fun assign(arg: String, idx: Int, allArguments: List<Argument>)

    fun checkRequired()

    fun deferrExec(): Boolean

    fun exec()

    fun printout(args: Array<String>?): String

    fun reset()

}