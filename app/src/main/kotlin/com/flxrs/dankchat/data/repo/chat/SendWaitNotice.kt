package com.flxrs.dankchat.data.repo.chat

internal sealed interface SendWaitNotice {
    val durationSeconds: Int

    data class SlowMode(
        override val durationSeconds: Int,
    ) : SendWaitNotice

    data class Timeout(
        override val durationSeconds: Int,
    ) : SendWaitNotice
}

internal fun parseSendWaitNotice(
    messageId: String?,
    message: String,
): SendWaitNotice? {
    val durationSeconds =
        when (messageId) {
            "msg_slowmode" ->
                SLOW_MODE_REMAINING_REGEX
                    .find(message)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()

            "msg_timedout" ->
                TIMEOUT_REMAINING_REGEX
                    .find(message)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()

            else -> null
        } ?: return null

    return when (messageId) {
        "msg_slowmode" -> SendWaitNotice.SlowMode(durationSeconds)
        "msg_timedout" -> SendWaitNotice.Timeout(durationSeconds)
        else -> null
    }
}

private val SLOW_MODE_REMAINING_REGEX = Regex("talk again in (\\d+) seconds")
private val TIMEOUT_REMAINING_REGEX = Regex("timed out for (\\d+) more seconds")
