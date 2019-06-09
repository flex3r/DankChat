package com.flxrs.dankchat.chat

import com.flxrs.dankchat.service.twitch.message.TwitchMessage

data class ChatItem(val message: TwitchMessage, val historic: Boolean = false) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as ChatItem
		return message.id == other.message.id
	}

	override fun hashCode(): Int {
		var result = message.hashCode()
		result = 31 * result + historic.hashCode()
		return result
	}
}