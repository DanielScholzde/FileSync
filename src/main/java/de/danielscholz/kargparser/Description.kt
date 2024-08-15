package de.danielscholz.kargparser

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Description(val value: String)