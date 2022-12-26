package com.flxrs.dankchat.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object DateTimeUtils {
    private var timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun setPattern(pattern: String) {
        timeFormatter = DateTimeFormatter.ofPattern(pattern)
    }

    fun timestampToLocalTime(ts: Long): String {
        return Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(timeFormatter)
    }

    fun String.asParsedZonedDateTime(): String = ZonedDateTime.parse(this).format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun formatSeconds(durationInSeconds: Int): String {

        val seconds = durationInSeconds % 60
        val timeoutMinutes = durationInSeconds / 60
        val minutes = timeoutMinutes % 60
        val timeoutHours = timeoutMinutes / 60
        val hours = timeoutHours % 24
        val timeoutDays = timeoutHours / 24
        val days = timeoutDays % 7
        val weeks = timeoutDays / 7

        return listOf(weeks to "w", days to "d", hours to "h", minutes to "m", seconds to "s")
            .filter { (time, _) -> time > 0 }
            .joinToString(" ") { (time, unit) -> "$time$unit" }
    }

    // TODO tests
    fun durationToSeconds(duration: String): Int? {
        if (duration.isBlank() || !duration.first().isDigit()) {
            return null
        }

        val checkSeconds = duration.toIntOrNull()
        if (checkSeconds != null) {
            return checkSeconds
        }

        var seconds = 0
        var acc = 0
        duration.forEach { c ->
            if (c.isDigit()) {
                acc = acc * 10 + c.digitToInt()
                return@forEach
            }

            if (acc == 0) {
                return null
            }

            val multiplier = secondsMultiplierForUnit(c) ?: return null
            seconds += acc * multiplier
            acc = 0
        }

        if (acc != 0) {
            return null
        }

        return seconds
    }

    private fun secondsMultiplierForUnit(char: Char): Int? = when (char) {
        's'  -> 1
        'm'  -> 60
        'h'  -> 60 * 60
        'd'  -> 60 * 60 * 24
        'w'  -> 60 * 60 * 24 * 7
        else -> null
    }
}