package de.danielscholz.kargparser

class Headline(private val text: String) : IParam {

    override fun init(argParser: ArgParser<*>, config: ArgParserConfig) {}

    override fun matches(arg: String, idx: Int, allArguments: List<ArgParser.Argument>): Boolean = false

    override fun assign(arg: String, idx: Int, allArguments: List<ArgParser.Argument>) {}

    override fun checkRequired() {}

    override fun deferrExec(): Boolean = false

    override fun exec() {}

    override fun printout(args: Array<String>?): String = text

    override fun reset() {}
}