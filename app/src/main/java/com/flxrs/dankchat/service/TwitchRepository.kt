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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
	private var loadedGlobalEmotes = false
	private var loadedTwitchEmotes = false
	private val connection: WebSocketConnection = get { parametersOf(::onDisconnect, ::onMessage) }

	fun getChat(channel: String): LiveData<List<ChatItem>> {
		var liveData = chatLiveDatas[channel]
		if (liveData == null) {
			liveData = MutableLiveData(emptyList())
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

	fun connectAndAddChannel(channel: String, nick: String, oAuth: String, id: Int, load3rdPartyEmotesAndBadges: Boolean = false, forceReconnect: Boolean = false) {
		if (forceReconnect) {
			hasConnected = false
			loadedTwitchEmotes = false
		}

		if (load3rdPartyEmotesAndBadges) scope.launch {
			loadBadges(channel)
			load3rdPartyEmotes(channel)
			loadRecentMessages(channel)
		}

		if (oAuth.isNotBlank() && oAuth.startsWith("oauth:") && !loadedTwitchEmotes) {
			loadedTwitchEmotes = true
			loadTwitchEmotes(oAuth.substringAfter("oauth:"), id)
		}

		if (hasConnected) {
			connection.joinChannel(channel)
		} else {
			connection.connect(nick, oAuth, channel)
			hasConnected = true
		}

	}

	fun partChannel(channel: String) {
		connection.partChannel(channel)
		chatLiveDatas[channel]?.postValue(emptyList())
		chatLiveDatas.remove("channel")
	}

	fun sendMessage(channel: String, message: String) = connection.sendMessage("PRIVMSG #$channel :$message")

	fun reconnect() = close(true)

	fun close(doReconnect: Boolean = false) {
		canType.keys.forEach { canType[it]?.postValue(false) }
		makeAndPostSystemMessage("Disconnected")
		connection.close(doReconnect)
	}

	@Synchronized
	private fun onDisconnect() {
		if (!hasDisconnected) {
			hasDisconnected = true
			close()
		}
	}

	fun clear(channel: String) {
		chatLiveDatas[channel]?.postValue(emptyList())
	}

	fun reloadEmotes(channel: String, oAuth: String, id: Int) = scope.launch {
		loadedGlobalEmotes = false
		loadedTwitchEmotes = false
		load3rdPartyEmotes(channel)
		if (id != 0 && oAuth.isNotBlank() && oAuth.startsWith("oauth:")) loadTwitchEmotes(oAuth.substringAfter("oauth:"), id)
	}

	private fun onMessage(msg: IrcMessage) {
		//Log.i(TAG, msg.raw)
		val parsed = TwitchMessage.parse(msg).map { ChatItem(it) }
		when (msg.command) {
			"366"       -> handleConnected(msg.params[1].substring(1))
			"CLEARCHAT" -> {
				val target = if (msg.params.size > 1) msg.params[1] else ""
				val channel = msg.params[0].substring(1)
				chatLiveDatas[channel]?.value?.replaceWithTimeOuts(target)?.run {
					add(parsed[0])
					chatLiveDatas[channel]?.postValue(this)
				}
			}
			else        -> if (parsed.isNotEmpty()) {
				val channel = msg.params[0].substring(1)
				val currentChat = chatLiveDatas[channel]?.value ?: emptyList()
				chatLiveDatas[channel]?.postValue(currentChat.addAndLimit(parsed))
			}
		}
	}

	private fun handleConnected(channel: String) {
		makeAndPostSystemMessage("Connected", channel)
		hasDisconnected = false
		if (!connection.isJustinFan) {
			canType[channel]?.postValue(true) ?: MutableLiveData(true)
		}
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

	private fun loadTwitchEmotes(oAuth: String, id: Int) = scope.launch {
		TwitchApi.getUserEmotes(oAuth, id)?.let {
			EmoteManager.setTwitchEmotes(it)
		}
	}

	private suspend fun load3rdPartyEmotes(channel: String) = withContext(Dispatchers.IO) {
		TwitchApi.getFFZChannelEmotes(channel)?.let { EmoteManager.setFFZEmotes(channel, it) }
		TwitchApi.getBTTVChannelEmotes(channel)?.let { EmoteManager.setBTTVEmotes(channel, it) }

		if (!loadedGlobalEmotes) {
			TwitchApi.getFFZGlobalEmotes()?.let { EmoteManager.setFFZGlobalEmotes(it) }
			TwitchApi.getBTTVGlobalEmotes()?.let { EmoteManager.setBTTVGlobalEmotes(it) }
			loadedGlobalEmotes = true
		}
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
		val current = chatLiveDatas[channel]?.value ?: emptyList()
		chatLiveDatas[channel]?.postValue(list.addAndLimit(current))
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