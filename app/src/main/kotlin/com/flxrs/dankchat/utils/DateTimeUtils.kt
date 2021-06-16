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
        var res = ""
        val totalSeconds = duration.toInt()

        val seconds = totalSeconds % 60
        val timeoutMinutes = totalSeconds / 60
        val minutes = timeoutMinutes % 60
        val timeoutHours = timeoutMinutes / 60
        val hours = timeoutHours % 24
        val days = timeoutHours / 24

        if (days > 0) {
            res = res.plus(days.toString() + "d")
        }
        if (hours > 0) {
            if (res.isNotEmpty()) {
                res = res.plus(" ")
            }
            res = res.plus(hours.toString() + "h")
        }
        if (minutes > 0) {
            if (res.isNotEmpty()) {
                res = res.plus(" ")
            }
            res = res.plus(minutes.toString() + "m")
        }
        if (seconds > 0) {
            if (res.isNotEmpty()) {
                res = res.plus(" ")
            }
            res = res.plus(seconds.toString() +  "s")
        }
        return res
    }
}