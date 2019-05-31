package com.flxrs.dankchat

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.service.TwitchRepository

class DankChatViewModel(private val twitchRepository: TwitchRepository) : ViewModel() {

	val chat = twitchRepository.getChat()
	val canType = twitchRepository.getCanType()

	fun connectOrJoinChannel(channel: String, oauth: String, name: String, loadEmotesAndBadges: Boolean = false, forceReconnect: Boolean = false) = twitchRepository.connectAndAddChannel(channel, oauth, name, loadEmotesAndBadges, forceReconnect)

	fun close() = twitchRepository.close()

	fun reconnect() = twitchRepository.reconnect()

	fun clear(channel: String) = twitchRepository.clear(channel)

	fun reloadEmotes(channel: String) = twitchRepository.reloadEmotes(channel)

	fun sendMessage(channel: String, message: String) {
		if (message.isNotBlank()) twitchRepository.sendMessage(channel, message)
	}
}