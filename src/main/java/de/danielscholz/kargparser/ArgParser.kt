package de.danielscholz.kargparser

class ArgParser<T> internal constructor(val paramValues: T, private val params: List<IParam>) {

    class Argument(val value: String, var matched: Boolean)

    companion object {
        const val descriptionMarker = ":DESCRIPTION:"
        val defaultConfig = ArgParserConfig()
    }

    private var parent: ArgParser<*>? = null
    private var config: ArgParserConfig = defaultConfig

    private val matchedParams: MutableList<IParam> = mutableListOf()

    private var argsToParse: Array<String> = arrayOf()


    internal fun init(parentArgParser: ArgParser<*>?, config: ArgParserConfig) {
        parent = parentArgParser
        this.config = config
        params.forEach { it.init(this, config) }

        val list = params.filterIsInstance<IActionParam>()
        if (list.map { it.name }.distinct().size != list.size) {
            throw RuntimeException("There are action commands that are registered with the same name!")
        }

        params.filterIsInstance<ValueParam>().dropWhile { !it.nameless() }.filter { !it.nameless() }.ifNotEmpty {
            throw RuntimeException("There are named parameter after nameless parameter: ${joinToString(", ") { it.name ?: "" }}")
        }

        params.dropWhile { it !is ValueParam || !it.nameless() }.filterIsInstance<IActionParam>().ifNotEmpty {
            throw RuntimeException("There are action parameter after nameless parameter: ${joinToString(", ") { it.name }}")
        }

        params.filterIsInstance<ValueParam>().dropWhile { !it.nameless() }.dropWhile { it.required }.dropWhile { !it.required }.ifNotEmpty {
            throw RuntimeException("There are required nameless parameter after not required nameless parameter: ${joinToString(", ") { it.description ?: "(no description)" }}")
        }

        params.filterIsInstance<ValueParam>().dropWhile { !it.nameless() }.filter { it.required }.ifNotEmpty {
            val list1 = this
            params.filterIsInstance<ActionParam<*>>().filter { it.subArgParser.hasNamelessNotRequiredParameter() }.ifNotEmpty {
                throw RuntimeException("There are required nameless parameter after not required nameless parameter: ${list1.joinToString(", ") { it.description ?: "(no description)" }}")
            }
        }
    }

    fun parseArgs(args: Array<String>) {
        if (parent != null) throw RuntimeException("Method parseArgs() should not be called on a subparser")

        argsToParse = args

        val arguments = args.map { Argument(it, false) }

        parseArgs(arguments)

        val list = arguments.filter { !it.matched && it.value != "--" }
        if (list.isNotEmpty()) {
            throw ArgParseException(list.joinToString(prefix = "Unassigned arguments: ") { it.value }, this)
        }

        checkRequired()

        exec()
    }

    internal fun parseArgs(arguments: List<Argument>) {
        for (param in params) {
            var i = -1
            for (arg in arguments) {
                i++
                if (arg.matched) continue

                if (param.matches(arg.value, i, arguments)) {
                    matchedParams.add(param)
                    arg.matched = true // must be set before the assign!
                    param.assign(arg.value, i, arguments)
                }
            }
        }
    }

    internal fun checkRequired() {
        params.filterIsInstance<ValueParam>().forEach { it.checkRequired() }
        matchedParams.filterIsInstance<IActionParam>().forEach { it.checkRequired() }
    }

    internal fun exec() {
        for (param in matchedParams) {
            if (!param.deferrExec()) param.exec()
        }
        for (param in matchedParams) {
            if (param.deferrExec()) param.exec()
        }
    }

    fun reset() {
        matchedParams.clear()
        argsToParse = arrayOf()
        params.forEach { it.reset() }
    }

    private fun getRootArgParser(): ArgParser<*> {
        var parser: ArgParser<*> = this
        do {
            parser = parser.parent ?: break
        } while (true)
        return parser
    }

    private fun getAllArgsToParse() = getRootArgParser().argsToParse

    private fun hasNamelessNotRequiredParameter() = params.filterIsInstance<ValueParam>().dropWhile { !it.nameless() }.filter { !it.required }.isNotEmpty()

    /**
     * @param rawOutput print output without any beginning sentence (e.g. "All supported parameters are:")?
     */
    fun printout(e: ArgParseException, rawOutput: Boolean = false): String {
        return printout(getAllArgsToParse(), e, rawOutput)
    }

    /**
     * @param rawOutput print output without any beginning sentence (e.g. "All supported parameters are:")?
     */
    fun printout(args: Array<String>? = null, rawOutput: Boolean = false): String {
        return printout(args, null, rawOutput)
    }

    internal fun printout(args: Array<String>?, e: ArgParseException? = null, rawOutput: Boolean = false): String {

        fun rightPad(str: String, len: Int): String {
            var s = str
            while (s.length < len) s += " "
            return s
        }

        val commandFound = if (!args.isNullOrEmpty()) {
            val allArgsToParse = args.map { Argument(it, false) }
            params.filterIsInstance<IActionParam>().any { action -> allArgsToParse.anyIndexed { idx, argument -> action.matches(argument.value, idx, allArgsToParse) } }
        } else false

        var str = params.map { it.printout(if (commandFound) args else null) }.filter { it.isNotEmpty() }.joinToString("\n")

        if (parent != null) {
            str = "   " + str.replace(Regex("\n"), "\n   ")
        }

        if (parent == null && str.contains(descriptionMarker)) {
            // get max length from all rows without description
            val maxRowLen = str.splitToSequence('\n')
                .map { if (it.contains(descriptionMarker)) it.indexOf(descriptionMarker) else 0 }
                .maxOrNull() ?: 0

            str = str.splitToSequence('\n')
                .map {
                    if (it.contains(descriptionMarker)) {
                        rightPad(it.substring(0, it.indexOf(descriptionMarker)), maxRowLen + 1) +
                                it.substring(it.indexOf(descriptionMarker) + descriptionMarker.length)
                    } else {
                        it
                    }
                }
                .joinToString("\n")
        }

        if (parent == null && str.contains("**")) {
            str = "$str\n\n** required"
        }

        if (parent == null && !rawOutput) {
            str = if (e != null) {
                "An error has occurred while processing the parameters: ${e.message}\nAll supported parameters are:\n$str"
            } else {
                "All supported parameters are:\n$str"
            }
        }
        return str
    }

}