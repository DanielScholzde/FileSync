package de.danielscholz.fileSync.common

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import java.time.Instant as JavaInstant
import java.time.LocalDateTime as JavaLocalDateTime
import java.time.temporal.ChronoField as JavaChronoField


//fun Instant.convertToLocalZone(): ZonedDateTime {
//    return this.atZone(zoneIdLocal)
//}
//
//fun Instant.convertToUtcZone(): ZonedDateTime {
//    return this.atZone(zoneIdUTC)
//}

fun JavaInstant.ignoreMillis(): JavaInstant {
    return this.with(JavaChronoField.NANO_OF_SECOND, 0)
}

fun JavaLocalDateTime.ignoreMillis(): JavaLocalDateTime = this.withNano(0)


val customFormat = LocalDateTime.Format {
    dayOfMonth()
    char('.')
    monthNumber()
    char('.')
    year()
    char(' ')
    hour()
    char(':')
    minute()
    char(':')
    second()
}

//val customFormat2 = LocalDateTime.Format {
//    year()
//    monthNumber()
//    dayOfMonth()
//}

fun Instant.toStr() = this.toLocalDateTime(TimeZone.currentSystemDefault()).format(customFormat)


// Formatter are thread-safe
//private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
//private val dateTimeFormatterSys = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss")!!
//private val dateTimeFormatterSys_nano = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.n")!!
//private val zoneIdLocal = ZoneId.systemDefault()
//private val zoneIdUTC = ZoneId.of("UTC")
//
//fun ZonedDateTime.toStr(): String {
//    return dateTimeFormatter.format(this)
//}
//
//fun ZonedDateTime.toStrSys(): String {
//    if (this.nano > 0) {
//        return dateTimeFormatterSys_nano.format(this).removeSuffix("000000")
//    }
//    return dateTimeFormatterSys.format(this)
//}
//
//fun ZonedDateTime.toStrFilename(): String {
//    return toStrSys()
//        .replace(Regex("[:]"), "")
//        .replace('-', '_')
//}