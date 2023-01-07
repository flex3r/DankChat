package com.flxrs.dankchat.data.twitch.message

import java.util.UUID

data class SystemMessage(
    val type: SystemMessageType,
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
) : Message()