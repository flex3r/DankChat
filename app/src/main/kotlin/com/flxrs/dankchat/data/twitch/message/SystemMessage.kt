package com.flxrs.dankchat.data.twitch.message

import java.util.*

data class SystemMessage(
    val type: SystemMessageType,
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: List<Highlight> = emptyList(),
) : Message()