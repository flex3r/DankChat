package com.flxrs.dankchat.utils

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

object DateTimeUtils {
    fun timestampToLocalTime(
        ts: Long,
        formatter: DateTimeFormatter,
    ): String = Instant
        .ofEpochMilli(ts)
        .atZone(ZoneId.systemDefault())
        .format(formatter)

    fun String.asParsedZonedDateTime(): String = ZonedDateTime
        .parse(this)
        .format(DateTimeFormatter.ISO_LOCAL_DATE)

    fun formatSeconds(durationInSeconds: Int): String {
        val seconds = durationInSeconds % 60
        val timeoutMinutes = durationInSeconds / 60
        val minutes = timeoutMinutes % 60
        val timeoutHours = timeoutMinutes / 60
        val hours = timeoutHours % 24
        val days = timeoutHours / 24

        return listOf(days to "d", hours to "h", minutes to "m", seconds to "s")
            .filter { (time, _) -> time > 0 }
            .joinToString(" ") { (time, unit) -> "$time$unit" }
    }

    fun durationToSeconds(duration: String): Int? {
        if (duration.isBlank() || !duration.first().isDigit()) {
            return null
        }

        val checkSeconds = duration.toIntOrNull()
        if (checkSeconds != null) {
            return checkSeconds
        }

        var seconds = 0
        var acc: Int? = null
        duration.forEach { c ->
            if (c.isWhitespace()) {
                return@forEach
            }

            if (c.isDigit()) {
                acc = (acc ?: 0) * 10 + c.digitToInt()
                return@forEach
            }

            val multiplier = secondsMultiplierForUnit(c) ?: return null
            val currentAcc = acc ?: return null
            seconds += currentAcc * multiplier
            acc = null
        }

        if (acc != null) {
            return null
        }

        return seconds
    }

    private fun secondsMultiplierForUnit(char: Char): Int? = when (char) {
        's' -> 1
        'm' -> 60
        'h' -> 60 * 60
        'd' -> 60 * 60 * 24
        'w' -> 60 * 60 * 24 * 7
        else -> null
    }

    enum class DurationUnit { WEEKS, DAYS, HOURS, MINUTES, SECONDS }

    data class DurationPart(
        val value: Int,
        val unit: DurationUnit,
    )

    fun decomposeMinutes(totalMinutes: Int): List<DurationPart> = buildList {
        var remaining = totalMinutes
        val weeks = remaining / 10080
        remaining %= 10080
        if (weeks > 0) add(DurationPart(weeks, DurationUnit.WEEKS))
        val days = remaining / 1440
        remaining %= 1440
        if (days > 0) add(DurationPart(days, DurationUnit.DAYS))
        val hours = remaining / 60
        remaining %= 60
        if (hours > 0) add(DurationPart(hours, DurationUnit.HOURS))
        if (remaining > 0) add(DurationPart(remaining, DurationUnit.MINUTES))
    }

    fun decomposeSeconds(totalSeconds: Int): List<DurationPart> = buildList {
        var remaining = totalSeconds
        val days = remaining / 86400
        remaining %= 86400
        if (days > 0) add(DurationPart(days, DurationUnit.DAYS))
        val hours = remaining / 3600
        remaining %= 3600
        if (hours > 0) add(DurationPart(hours, DurationUnit.HOURS))
        val minutes = remaining / 60
        remaining %= 60
        if (minutes > 0) add(DurationPart(minutes, DurationUnit.MINUTES))
        if (remaining > 0) add(DurationPart(remaining, DurationUnit.SECONDS))
    }

    fun calculateUptime(startedAtString: String): String {
        val startedAt = Instant.parse(startedAtString).atZone(ZoneId.systemDefault()).toEpochSecond()
        val now = ZonedDateTime.now().toEpochSecond()

        val duration = now.seconds - startedAt.seconds
        val uptime =
            duration.toComponents { days, hours, minutes, _, _ ->
                buildString {
                    if (days > 0) append("${days}d ")
                    if (hours > 0) append("${hours}h ")
                    append("${minutes}m")
                }
            }

        return uptime
    }
}
