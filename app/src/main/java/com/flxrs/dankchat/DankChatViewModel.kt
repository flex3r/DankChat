package com.flxrs.dankchat

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.service.TwitchRepository

class DankChatViewModel(private val twitchRepository: TwitchRepository) : ViewModel() {

	fun getChat(channel: String) = twitchRepository.getChat(channel)

	fun getCanType(channel: String) = twitchRepository.getCanType(channel)

	fun getEmoteKeywords(channel: String) = twitchRepository.getEmoteKeywords(channel)

	fun connectOrJoinChannel(channel: String, nick: String, oauth: String, id: Int, loadEmotesAndBadges: Boolean = false, forceReconnect: Boolean = false) {
		twitchRepository.connectAndAddChannel(channel, nick, oauth, id, loadEmotesAndBadges, forceReconnect)
	}

	fun partChannel(channel: String) = twitchRepository.partChannel(channel)

	fun close() = twitchRepository.close()

	fun reconnect(onlyIfNecessary: Boolean = false) = twitchRepository.reconnect(onlyIfNecessary)

	fun clear(channel: String) = twitchRepository.clear(channel)

	fun reloadEmotes(channel: String, oauth: String, id: Int) = twitchRepository.reloadEmotes(channel, oauth, id)

	fun sendMessage(channel: String, message: String) {
		if (message.isNotBlank()) twitchRepository.sendMessage(channel, message)
	}
}