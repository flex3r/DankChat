package com.flxrs.dankchat.data.twitch.command

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.message.RoomState


data class CommandContext(
    val trigger: String,
    val channel: UserName,
    val channelId: UserId,
    val roomState: RoomState,
    val originalMessage: String,
    val args: List<String>
)
