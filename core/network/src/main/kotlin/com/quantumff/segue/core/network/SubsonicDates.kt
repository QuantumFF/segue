package com.quantumff.segue.core.network

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Subsonic timestamps are ISO 8601 with a zone, e.g. `2024-05-01T08:00:00.123Z`. */
object SubsonicDates {
    /** Navidrome sends Go's zero time for "never". Anything before 1970 is treated the same. */
    fun toEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli().takeIf { it > 0 }
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
