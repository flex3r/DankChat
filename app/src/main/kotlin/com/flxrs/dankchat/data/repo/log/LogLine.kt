package com.flxrs.dankchat.data.repo.log

import androidx.compose.runtime.Immutable

enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ;

    companion object {
        fun fromString(value: String): LogLevel = when (value.trim().uppercase()) {
            "TRACE" -> TRACE
            "DEBUG" -> DEBUG
            "INFO" -> INFO
            "WARN" -> WARN
            "ERROR" -> ERROR
            else -> DEBUG
        }
    }
}

@Immutable
data class LogLine(
    val index: Int,
    val raw: String,
    val timestamp: String,
    val level: LogLevel,
    val thread: String,
    val logger: String,
    val message: String,
)

object LogLineParser {
    // Matches: 2026-04-04 20:15:30.123 [main] DEBUG c.f.dankchat.SomeClass - Some message
    private val LOG_LINE_REGEX = Regex(
        """^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[(.+?)] (\w+)\s+(\S+) - (.*)$""",
    )

    fun parseAll(lines: List<String>): List<LogLine> {
        val result = mutableListOf<LogLine>()
        for (line in lines) {
            val match = LOG_LINE_REGEX.matchEntire(line)
            if (match != null) {
                val (timestamp, thread, level, logger, message) = match.destructured
                result += LogLine(
                    index = result.size,
                    raw = line,
                    timestamp = timestamp,
                    level = LogLevel.fromString(level),
                    thread = thread,
                    logger = logger,
                    message = message,
                )
            } else if (result.isNotEmpty()) {
                // Continuation line (e.g. stacktrace) — append to previous entry
                val previous = result.last()
                result[result.lastIndex] = previous.copy(
                    raw = previous.raw + "\n" + line,
                    message = previous.message + "\n" + line,
                )
            }
        }
        return result
    }
}
