package com.flxrs.dankchat.service

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.message.TwitchMessage
import com.flxrs.dankchat.utils.addAndLimit
import com.flxrs.dankchat.utils.replaceWithTimeOuts
import kotlinx.coroutines.*
import org.json.JSONObject
import org.koin.core.parameter.parametersOf
import org.koin.standalone.KoinComponent
import org.koin.standalone.get
import java.net.URL

class TwitchRepository(private val scope: CoroutineScope) : KoinComponent {

	private val chat = MutableLiveData<Map<String, List<ChatItem>>>(mutableMapOf())
	private val canType = MutableLiveData<Map<String, Boolean>>(mutableMapOf())
	private var hasConnected = false
	private val connection: WebSocketConnection = get { parametersOf(::onMessage) }

	fun getChat(): LiveData<Map<String, List<ChatItem>>> = chat

	fun getCanType(): LiveData<Map<String, Boolean>> = canType

	fun connectAndAddChannel(channel: String, oAuth: String, name: String, loadEmotesAndBadges: Boolean = false, forceReconnect: Boolean = false) {
		if (forceReconnect) hasConnected = false

		if (loadEmotesAndBadges) scope.launch { loadBadges(channel) }

		if (hasConnected) {
			connection.joinChannel(channel)
		} else {
			connection.connect(name, oAuth, channel)
			hasConnected = true
		}

		if (loadEmotesAndBadges) scope.launch {
			load3rdPartyEmotes(channel)
			loadRecentMessages(channel)
		}
	}

	fun sendMessage(channel: String, message: String) {
		connection.sendMessage("PRIVMSG #$channel :$message")
	}

	fun close() {
		val map = canType.value?.toMutableMap() ?: mutableMapOf()
		map.keys.forEach { map[it] = false }
		canType.postValue(map)

		connection.close()
		scope.coroutineContext.cancel()
		makeAndPostSystemMessage("Disconnected")
	}

	fun reconnect() {
		close()
	}

	fun clear(channel: String) {
		val map = chat.value ?: mapOf()
		val current = map[channel]
		current?.let {
			chat.postValue(map.plus(channel to emptyList()))
		}
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
		val map = canType.value?.toMutableMap() ?: mutableMapOf()
		Log.d(TAG, "${connection.isJustinFan}")
		if (!connection.isJustinFan) {
			map[channel] = true
			canType.postValue(map)
		}
	}

	private fun handleHostTarget(message: IrcMessage): Nothing = TODO()

	private fun handleClearMsg(message: IrcMessage): Nothing = TODO()

	private fun handleClearChat(message: IrcMessage) {
		val channel = message.params[0].substring(1)
		val target = if (message.params.size > 1) message.params[1] else ""
		val duration = message.tags["ban-duration"] ?: "idk"
		val systemMessage = if (target.isBlank()) "Chat has been cleared by a moderator." else "$target has been timed out for ${duration}s."
		val map = chat.value ?: mapOf()
		val current = map[channel]
		current?.replaceWithTimeOuts(target)?.run {
			add(ChatItem(TwitchMessage.makeSystemMessage(systemMessage, channel)))
			chat.postValue(map.plus(channel to this))
		}
	}

	private fun handleUserNotice(message: IrcMessage) {
		val map = chat.value ?: mapOf()
		val channel = message.params[0].substring(1)
		val current = map[channel] ?: emptyList()
		TwitchMessage.parseUserNotice(message).forEach {
			current.addAndLimit(ChatItem(it))
		}
		chat.postValue(map.plus(channel to current))
	}

	private fun handleNotice(message: IrcMessage) {
		val map = chat.value ?: mapOf()
		val channel = message.params[0].substring(1)
		val notice = message.params[1]
		val twitchMessage = TwitchMessage.makeSystemMessage(notice, channel)
		val current = map[channel] ?: emptyList()
		val pair = channel to current.addAndLimit(ChatItem(twitchMessage))
		chat.postValue(map.plus(pair))
	}

	private fun makeAndPostMessage(message: IrcMessage) {
		val twitchMessage = TwitchMessage.parseFromIrc(message)
		val channel = twitchMessage.channel
		val map = chat.value ?: mapOf()
		val current = map[channel] ?: emptyList()
		val pair = channel to current.addAndLimit(ChatItem(twitchMessage))
		chat.postValue(map.plus(pair))
	}

	private fun makeAndPostSystemMessage(message: String, channel: String = "") {
		var map = chat.value ?: mapOf()
		if (channel.isBlank()) {
			map.keys.forEach {
				val current = map[it] ?: emptyList()
				val pair = it to current.addAndLimit(ChatItem(TwitchMessage.makeSystemMessage(message, it)))
				map = map.plus(pair)
			}
		} else {
			val current = map[channel] ?: emptyList()
			val pair = channel to current.addAndLimit(ChatItem(TwitchMessage.makeSystemMessage(message, channel)))
			map = map.plus(pair)
		}
		chat.postValue(map)
	}

	private suspend fun loadBadges(channel: String) = withContext(Dispatchers.IO) {
		launch { EmoteManager.loadGlobalBadges() }
		launch {
			val id = EmoteManager.getUserIdFromName(channel)
			EmoteManager.loadChannelBadges(id, channel)
		}
	}

	private suspend fun load3rdPartyEmotes(channel: String) = withContext(Dispatchers.IO) {
		launch { EmoteManager.loadFfzEmotes(channel) }
		launch { EmoteManager.loadBttvEmotes(channel) }
		launch { EmoteManager.loadGlobalBttvEmotes() }
	}

	private suspend fun loadRecentMessages(channel: String) = withContext(Dispatchers.IO) {
		val list = mutableListOf<ChatItem>()
		val response = URL("$RECENT_MSG_URL$channel?clearchatToNotice=true").readText()
		withContext(Dispatchers.Default) {
			val json = JSONObject(response)
			val messages = json.getJSONArray("messages")
			for (i in 0 until messages.length()) {
				val message = IrcMessage.parse(messages.getString(i))
				val twitchMessage = when (message.tags["display-name"]) {
					"NOTICE"     -> parseRecentNotice(message)
					"CLEARCHAT"  -> parseRecentNotice(message)
					"USERNOTICE" -> parseRecentUserNotice(message)
					else         -> TwitchMessage.parseFromIrc(message, isHistoric = true)
				}
				list.add(ChatItem(twitchMessage))
			}
			val map = chat.value ?: mapOf()
			val current = map[channel] ?: listOf()
			chat.postValue(map.plus(channel to list.plus(current)))
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
		private const val RECENT_MSG_URL = "https://recent-messages.robotty.de/api/v2/recent-messages/"
	}
}