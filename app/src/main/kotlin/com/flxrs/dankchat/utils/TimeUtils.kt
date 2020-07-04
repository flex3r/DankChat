package com.flxrs.dankchat.utils

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun timestampToLocalTime(ts: Long): String {
        return Instant.ofEpochMilli(ts).atZone(ZonedDateTime.now().zone).format(timeFormatter)
    }

    fun localTime(): String {
        return ZonedDateTime.now().format(timeFormatter)
    }
}