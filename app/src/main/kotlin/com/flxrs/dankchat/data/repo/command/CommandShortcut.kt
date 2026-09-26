package com.flxrs.dankchat.data.repo.command

import com.flxrs.dankchat.data.UserName

enum class CommandShortcut(
    val trigger: String,
) {
    ReplyToLastWhisper(trigger = "/r"),
}

internal fun expandReplyToLastWhisper(
    text: String,
    lastReceivedWhisperUser: UserName?,
): String? {
    val trigger = CommandShortcut.ReplyToLastWhisper.trigger
    if (lastReceivedWhisperUser == null || !text.startsWith("$trigger ", ignoreCase = true)) return null
    return "/w ${lastReceivedWhisperUser.value}${text.substring(trigger.length)}"
}
