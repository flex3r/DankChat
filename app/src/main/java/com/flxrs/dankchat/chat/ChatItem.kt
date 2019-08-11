package com.flxrs.dankchat.chat

import com.flxrs.dankchat.service.twitch.message.TwitchMessage

data class ChatItem(val message: TwitchMessage, val historic: Boolean = false)