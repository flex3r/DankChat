package com.flxrs.dankchat.data.twitch.message

data class MessageThread(
    val rootMessageId: String,
    val rootMessage: PrivMessage,
    val replies: List<PrivMessage>,
    val participated: Boolean,
    // Item tag by message id, raised whenever a message in the thread changes
    val itemTags: Map<String, Int> = emptyMap(),
)
