package com.flxrs.dankchat.data.twitch.message

data class MessageThread(val rootMessageId: String, val rootMessage: PrivMessage, val replies: List<PrivMessage>, val participated: Boolean)
