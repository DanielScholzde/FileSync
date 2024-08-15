package de.danielscholz.kargparser

import kotlin.reflect.KMutableProperty0
import kotlin.reflect.full.findAnnotation

class ArgParserBuilder<T>(val paramValues: T) {

    private var createdArgParser: ArgParser<T>? = null
    private val params = mutableListOf<IParam>()

    fun buildWith(config: ArgParserConfig = ArgParserConfig(), init: ArgParserBuilder<T>.() -> Unit): ArgParser<T> {
        init()
        val argParser = ArgParser(paramValues, params)
        argParser.init(null, config) // parentArgParser will be set later if it is a subparser
        createdArgParser = argParser
        return argParser
    }

    fun add(param: IParam) {
        params.add(param)
    }

    fun addHeadline(headline: String) {
        params.add(Headline(headline))
    }

    fun <S, R : S> add(property: KMutableProperty0<S>, parser: IValueParamParser<out R, S>, description: String? = null, required: Boolean = false) {
        parser.callback = { property.setter.call(it) }
        val descriptionStr = description ?: property.findAnnotation<Description>()?.value
        params.add(ValueParam(property.name, descriptionStr, required, parser.convertToStr(property.getter.call())).addParser(parser))
    }

    fun <S, R : S> addNamelessLast(property: KMutableProperty0<S>, parser: IValueParamParser<out R, S>, description: String? = null, required: Boolean = false) {
        parser.callback = { property.setter.call(it) }
        val descriptionStr = description ?: property.findAnnotation<Description>()?.value
        params.add(ValueParam(null, descriptionStr, required, parser.convertToStr(property.getter.call())).addParser(parser))
    }

    fun addActionParser(name: String, description: String? = null, callback: () -> Unit) {
        params.add(ActionParamSimple(name, description, callback))
    }

    fun <U> addActionParser(
        name: String,
        subArgParser: ArgParser<U>,
        description: String? = null,
        callbackBeforeSubParameterParsing: () -> Unit = {},
        callback: ArgParser<U>.() -> Unit
    ) {
        params.add(ActionParam(name, description, subArgParser, callbackBeforeSubParameterParsing, callback))
    }

    fun printout(): String {
        return createdArgParser?.printout() ?: ""
    }
}