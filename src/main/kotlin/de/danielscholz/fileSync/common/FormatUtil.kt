package de.danielscholz.fileSync.common

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.time.Instant


fun Long.formatAsFileSize(): String {
    val size = this
    val gb = size * 10 / (1024 * 1024 * 1024)
    if (gb.absoluteValue >= 10) {
        return "${gb / 10.0} GB".replace('.', ',')
    }
    val mb = size * 10 / (1024 * 1024)
    if (mb.absoluteValue >= 10) {
        return "${mb / 10.0} MB".replace('.', ',')
    }
    val kb = size * 10 / 1024
    if (kb.absoluteValue >= 10) {
        return "${kb / 10.0} KB".replace('.', ',')
    }
    return "$size Byte"
}

private val customFormat = LocalDateTime.Format {
    day()
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