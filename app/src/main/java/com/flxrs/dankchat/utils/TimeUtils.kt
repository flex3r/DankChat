package com.flxrs.dankchat.utils

import org.threeten.bp.Instant
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter

object TimeUtils {
	private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

	fun timestampToLocalTime(ts: Long): String {
		return Instant.ofEpochMilli(ts).atZone(ZonedDateTime.now().zone).format(timeFormatter)
	}

	fun localTime(): String {
		return ZonedDateTime.now().format(timeFormatter)
	}
}