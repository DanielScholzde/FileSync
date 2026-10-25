package de.danielscholz.fileSync.common

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

