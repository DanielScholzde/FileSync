package de.danielscholz.kargparser.parser

import de.danielscholz.kargparser.ArgParseException

class EnumParam<T : Enum<T>>(private val enumType: T, private val typeDescription: String? = null) : ParamParserBase<T, T?>() {

    override var callback: ((T) -> Unit)? = null
    private var value: T? = null

    override fun matches(rawValue: String): Boolean {
        for (enumConstant in enumType.declaringJavaClass.enumConstants) {
            if (enumConstant.name.equals(rawValue, true)) {
                return true
            }
        }
        return false
    }

    override fun assign(rawValue: String) {
        for (enumConstant in enumType.declaringJavaClass.enumConstants) {
            if (enumConstant.name.equals(rawValue, true)) {
                value = enumConstant
                return
            }
        }
    }

    override fun exec() {
        callback?.invoke(value!!) ?: throw ArgParseException("callback must be specified!", argParser!!)
    }

    override fun printout(): String {
        return typeDescription ?: "value"
    }
}