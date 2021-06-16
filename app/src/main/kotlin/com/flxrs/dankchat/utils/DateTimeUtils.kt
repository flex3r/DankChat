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

    fun formatSeconds(duration: String): String {
        val totalSeconds = duration.toIntOrNull() ?: return ""

        val seconds = totalSeconds % 60
        val timeoutMinutes = totalSeconds / 60
        val minutes = timeoutMinutes % 60
        val timeoutHours = timeoutMinutes / 60
        val hours = timeoutHours % 24
        val days = timeoutHours / 24

        return listOf(days to "d", hours to "h", minutes to "m", seconds to "s")
            .filter { (time, _) -> time > 0 }
            .joinToString(" ") { (time, unit) -> "$time$unit" }
    }
}