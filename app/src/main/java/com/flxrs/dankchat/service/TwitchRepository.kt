package com.flxrs.dankchat.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.message.TwitchMessage
import com.flxrs.dankchat.utils.addAndLimit
import com.flxrs.dankchat.utils.replaceWithTimeOuts
import kotlinx.coroutines.*
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.parameter.parametersOf

class TwitchRepository(private val scope: CoroutineScope) : KoinComponent {


	private val chatLiveDatas = mutableMapOf<String, MutableLiveData<List<ChatItem>>>()
	private val canType = mutableMapOf<String, MutableLiveData<Boolean>>()
	private val emoteKeywords = mutableMapOf<String, MutableLiveData<List<String>>>()
	private var hasConnected = false
	private var hasDisconnected = false
	private var loadedGlobalBadges = false
	private val connection: WebSocketConnection = get { parametersOf(::onDisconnect, ::onMessage) }

	fun getChat(channel: String): LiveData<List<ChatItem>> {
		var liveData = chatLiveDatas[channel]
		if (liveData == null) {
			liveData = MutableLiveData(listOf())
			chatLiveDatas[channel] = liveData
		}
		return liveData
	}

	fun getCanType(channel: String): LiveData<Boolean> {
		var liveData = canType[channel]
		if (liveData == null) {
			liveData = MutableLiveData(false)
			canType[channel] = liveData
		}
		return liveData
	}

	fun getEmoteKeywords(channel: String): LiveData<List<String>> {
		var liveData = emoteKeywords[channel]
		if (liveData == null) {
			liveData = MutableLiveData(emptyList())
			emoteKeywords[channel] = liveData
		}
		return liveData
	}

	fun connectAndAddChannel(channel: String, nick: String, oAuth: String, loadEmotesAndBadges: Boolean = false, forceReconnect: Boolean = false) {
		if (forceReconnect) hasConnected = false

		if (loadEmotesAndBadges) scope.launch {
			loadBadges(channel)
			load3rdPartyEmotes(channel)
			loadRecentMessages(channel)
		}

		if (hasConnected) {
			connection.joinChannel(channel)
		} else {
			connection.connect(nick, oAuth, channel)
			hasConnected = true
		}

	}

	fun partChannel(channel: String) = connection.partChannel(channel)

	fun sendMessage(channel: String, message: String) = connection.sendMessage("PRIVMSG #$channel :$message")

	fun reconnect() = close(true)

	fun close(doReconnect: Boolean = false) {
		canType.keys.forEach { canType[it]?.postValue(false) }
		makeAndPostSystemMessage("Disconnected")

		scope.coroutineContext.cancel()
		scope.coroutineContext.cancelChildren()
		connection.close(doReconnect)
	}

	@Synchronized
	private fun onDisconnect() {
		scope.coroutineContext.cancel()
		scope.coroutineContext.cancelChildren()

		if (!hasDisconnected) {
			hasDisconnected = true
			close()
		}
	}

	fun clear(channel: String) {
		chatLiveDatas[channel]?.postValue(emptyList())
	}

	fun reloadEmotes(channel: String) = scope.launch {
		loadBadges(channel)
		load3rdPartyEmotes(channel)
	}

	private fun onMessage(msg: IrcMessage) {
		//Log.i(TAG, msg.raw)
		when (msg.command) {
			"366"        -> handleConnected(msg.params[1].substring(1))
			"PRIVMSG"    -> makeAndPostMessage(msg)
			"NOTICE"     -> handleNotice(msg)
			"USERNOTICE" -> handleUserNotice(msg)
			"CLEARCHAT"  -> handleClearChat(msg)
			"CLEARMSG"   -> handleClearMsg(msg)
			"HOSTTARGET" -> handleHostTarget(msg)
			else         -> Unit
		}
	}

	private fun handleConnected(channel: String) {
		makeAndPostSystemMessage("Connected", channel)
		hasDisconnected = false
		if (!connection.isJustinFan) {
			canType[channel]?.postValue(true) ?: MutableLiveData(true)
		}
	}

	private fun handleHostTarget(message: IrcMessage) {
		//TODO implement
	}

	private fun handleClearMsg(message: IrcMessage) {
		//TODO implement
	}

	private fun handleClearChat(message: IrcMessage) {
		val channel = message.params[0].substring(1)
		val target = if (message.params.size > 1) message.params[1] else ""
		val duration = message.tags["ban-duration"] ?: "idk"
		val systemMessage = if (target.isBlank()) "Chat has been cleared by a moderator." else "$target has been timed out for ${duration}s."
		chatLiveDatas[channel]?.value?.replaceWithTimeOuts(target)?.run {
			add(ChatItem(TwitchMessage.makeSystemMessage(systemMessage, channel)))
			chatLiveDatas[channel]?.postValue(this)
		}
	}

	private fun handleUserNotice(message: IrcMessage) {
		val channel = message.params[0].substring(1)
		val currentChat = chatLiveDatas[channel]?.value ?: emptyList()
		TwitchMessage.parseUserNotice(message).forEach {
			currentChat.addAndLimit(ChatItem(it))
		}
		chatLiveDatas[channel]?.postValue(currentChat)
	}

	private fun handleNotice(message: IrcMessage) {
		val channel = message.params[0].substring(1)
		val notice = message.params[1]
		val twitchMessage = TwitchMessage.makeSystemMessage(notice, channel)
		val currentChat = chatLiveDatas[channel]?.value ?: emptyList()
		chatLiveDatas[channel]?.postValue(currentChat.addAndLimit(ChatItem(twitchMessage)))
	}

	private fun makeAndPostMessage(message: IrcMessage) {
		val twitchMessage = TwitchMessage.parseFromIrc(message)
		val channel = twitchMessage.channel
		val currentChat = chatLiveDatas[channel]?.value ?: emptyList()
		chatLiveDatas[channel]?.postValue(currentChat.addAndLimit(ChatItem(twitchMessage)))
	}

	private fun makeAndPostSystemMessage(message: String, channel: String = "") {
		if (channel.isBlank()) {
			chatLiveDatas.keys.forEach {
				val currentChat = chatLiveDatas[it]?.value ?: emptyList()
				chatLiveDatas[it]?.postValue(currentChat.addAndLimit(ChatItem(TwitchMessage.makeSystemMessage(message, it))))
			}
		} else {
			val currentChat = chatLiveDatas[channel]?.value ?: emptyList()
			chatLiveDatas[channel]?.postValue(currentChat.addAndLimit(ChatItem(TwitchMessage.makeSystemMessage(message, channel))))
		}
	}

	private suspend fun loadBadges(channel: String) = withContext(Dispatchers.IO) {
		if (!loadedGlobalBadges) TwitchApi.getGlobalBadges()?.let {
			EmoteManager.setGlobalBadges(it)
			loadedGlobalBadges = true
		}
		TwitchApi.getChannelBadges(channel)?.let { EmoteManager.setChannelBadges(channel, it) }
	}

	private suspend fun load3rdPartyEmotes(channel: String) = withContext(Dispatchers.IO) {
		TwitchApi.getFFZChannelEmotes(channel)?.let { EmoteManager.setFFZEmotes(channel, it) }
		TwitchApi.getFFZGlobalEmotes()?.let { EmoteManager.setFFZGlobalEmotes(it) }
		TwitchApi.getBTTVChannelEmotes(channel)?.let { EmoteManager.setBTTVEmotes(channel, it) }
		TwitchApi.getBTTVGlobalEmotes()?.let { EmoteManager.setBTTVGlobalEmotes(it) }
		val keywords = EmoteManager.getEmoteKeywords(channel)
		emoteKeywords[channel]?.postValue(keywords)
	}

	private suspend fun loadRecentMessages(channel: String) = withContext(Dispatchers.Default) {
		val list = mutableListOf<ChatItem>()
		TwitchApi.getRecentMessages(channel)?.messages?.forEach {
			val message = IrcMessage.parse(it)
			val twitchMessage = when (message.tags["display-name"]) {
				"NOTICE"     -> parseRecentNotice(message)
				"CLEARCHAT"  -> parseRecentNotice(message)
				"USERNOTICE" -> parseRecentUserNotice(message)
				else         -> TwitchMessage.parseFromIrc(message, isHistoric = true)
			}
			list.add(ChatItem(twitchMessage, true))
		}
		withContext(Dispatchers.Main) {
			val current = chatLiveDatas[channel]?.value ?: emptyList()
			val modified = current.map { ChatItem(it.message, true) }
			chatLiveDatas[channel]?.value = list.addAndLimit(modified)
		}
	}

	private fun parseRecentNotice(message: IrcMessage): TwitchMessage {
		val channel = message.params[0].substring(1)
		val notice = message.params[1]
		return TwitchMessage.makeSystemMessage(notice, channel)
	}

	private fun parseRecentUserNotice(message: IrcMessage): TwitchMessage = TwitchMessage.parseUserNotice(message, true)[0]

	companion object {
		private val TAG = TwitchRepository::class.java.simpleName
	}
}