package com.flxrs.dankchat.utils

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DateTimeUtils {
    private var timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun setPattern(pattern: String) {
        timeFormatter = DateTimeFormatter.ofPattern(pattern)
    }

    fun timestampToLocalTime(ts: Long): String {
        return Instant.ofEpochMilli(ts).atZone(ZonedDateTime.now().zone).format(timeFormatter)
    }

    fun String.asParsedZonedDateTime(): String = ZonedDateTime.parse(this).format(DateTimeFormatter.ISO_LOCAL_DATE)
}