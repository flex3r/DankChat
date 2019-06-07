package com.flxrs.dankchat

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.service.TwitchRepository

class DankChatViewModel(private val twitchRepository: TwitchRepository) : ViewModel() {

	fun getChat(channel: String) = twitchRepository.getChat(channel)

	fun getCanType(channel: String) = twitchRepository.getCanType(channel)

	fun getEmoteKeywords(channel: String) = twitchRepository.getEmoteKeywords(channel)

	fun connectOrJoinChannel(channel: String, nick: String, oauth: String, loadEmotesAndBadges: Boolean = false, forceReconnect: Boolean = false) {
		twitchRepository.connectAndAddChannel(channel, nick, oauth, loadEmotesAndBadges, forceReconnect)
	}

	fun partChannel(channel: String) = twitchRepository.partChannel(channel)

	fun close() = twitchRepository.close()

	fun reconnect() = twitchRepository.reconnect()

	fun clear(channel: String) = twitchRepository.clear(channel)

	fun reloadEmotes(channel: String) = twitchRepository.reloadEmotes(channel)

	fun sendMessage(channel: String, message: String) {
		if (message.isNotBlank()) twitchRepository.sendMessage(channel, message)
	}
}