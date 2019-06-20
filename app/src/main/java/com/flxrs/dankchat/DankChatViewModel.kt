package com.flxrs.dankchat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.TwitchRepository
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import java.io.File

class DankChatViewModel(private val twitchRepository: TwitchRepository) : ViewModel() {

	val imageUploadedEvent = twitchRepository.imageUploadedEvent

	fun getChat(channel: String): LiveData<List<ChatItem>> = twitchRepository.getChat(channel)

	fun getCanType(channel: String): LiveData<String> = twitchRepository.getCanType(channel)

	fun getEmoteKeywords(channel: String): LiveData<List<GenericEmote>> = twitchRepository.getEmoteKeywords(channel)

	fun connectOrJoinChannel(channel: String, nick: String, oauth: String, id: Int, loadEmotesAndBadges: Boolean = false, doReauth: Boolean = false) {
		twitchRepository.connectAndAddChannel(channel, nick, oauth, id, loadEmotesAndBadges, doReauth)
	}

	fun partChannel(channel: String) = twitchRepository.partChannel(channel)

	fun close(onClosed: () -> Unit) = twitchRepository.close(onClosed)

	fun reconnect(onlyIfNecessary: Boolean = false) = twitchRepository.reconnect(onlyIfNecessary)

	fun clear(channel: String) = twitchRepository.clear(channel)

	fun reloadEmotes(channel: String, oauth: String, id: Int) = twitchRepository.reloadEmotes(channel, oauth, id)

	fun sendMessage(channel: String, message: String) {
		if (message.isNotBlank()) twitchRepository.sendMessage(channel, message)
	}

	fun uploadImage(file: File) = twitchRepository.uploadImage(file)
}