package com.flxrs.dankchat.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.service.twitch.message.TwitchMessage
import com.flxrs.dankchat.utils.SingleLiveEvent
import com.flxrs.dankchat.utils.addAndLimit
import com.flxrs.dankchat.utils.replaceWithTimeOuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.KoinComponent
import java.io.File
import java.nio.ByteBuffer
import kotlin.collections.set

class TwitchRepository(private val scope: CoroutineScope) : KoinComponent {

    private val chatLiveDatas = mutableMapOf<String, MutableLiveData<List<ChatItem>>>()
    private val canType = mutableMapOf<String, MutableLiveData<String>>()
    private val emoteSuggestions = mutableMapOf<String, MutableLiveData<List<GenericEmote>>>()
    private val roomStates = mutableMapOf<String, MutableLiveData<TwitchMessage.Roomstate>>()

    private var hasDisconnected = true
    private var loadedGlobalBadges = false
    private var loadedGlobalEmotes = false
    private var loadedTwitchEmotes = false
    private var lastMessage = ""

    val imageUploadedEvent = SingleLiveEvent<Pair<String, File>>()

    fun getChat(channel: String): LiveData<List<ChatItem>> {
        var liveData = chatLiveDatas[channel]
        if (liveData == null) {
            liveData = MutableLiveData(emptyList())
            chatLiveDatas[channel] = liveData
        }
        return liveData
    }

    fun getCanType(channel: String): LiveData<String> {
        var liveData = canType[channel]
        if (liveData == null) {
            liveData = MutableLiveData("Disconnected")
            canType[channel] = liveData
        }
        return liveData
    }

    fun getEmoteKeywords(channel: String): LiveData<List<GenericEmote>> {
        var liveData = emoteSuggestions[channel]
        if (liveData == null) {
            liveData = MutableLiveData(emptyList())
            emoteSuggestions[channel] = liveData
        }
        return liveData
    }

    fun getRoomState(channel: String): LiveData<TwitchMessage.Roomstate> {
        var liveData = roomStates[channel]
        if (liveData == null) {
            liveData = MutableLiveData(TwitchMessage.Roomstate(channel))
            roomStates[channel] = liveData
        }
        return liveData
    }

    fun loadData(channel: String, oAuth: String, id: Int, load3rdParty: Boolean, reAuth: Boolean) {
        if (reAuth) {
            loadedTwitchEmotes = false
        }

        scope.launch {
            if (load3rdParty) {
                loadBadges(channel)
                load3rdPartyEmotes(channel)
                loadRecentMessages(channel)
            }
            if (oAuth.isNotBlank() && oAuth.startsWith("oauth:")) {
                loadTwitchEmotes(oAuth.substringAfter("oauth:"), id)
            }

            setSuggestions(channel)
        }
    }

    fun removeChannelData(channel: String) {
        chatLiveDatas[channel]?.postValue(emptyList())
        chatLiveDatas.remove(channel)
    }

    fun sendMessage(channel: String, message: String, onResult: (msg: String) -> Unit) {
        if (message.isNotBlank()) {
            val messageWithSuffix = if (lastMessage == message) "$message $INVISIBLE_CHAR" else message
            lastMessage = messageWithSuffix
            onResult("PRIVMSG #$channel :$messageWithSuffix")
        }
    }

    @Synchronized
    fun handleDisconnect() {
        if (!hasDisconnected) {
            hasDisconnected = true
            canType.keys.forEach { canType[it]?.postValue("Disconnected") }
            makeAndPostSystemMessage("Disconnected")
        }
    }

    fun clear(channel: String) {
        chatLiveDatas[channel]?.postValue(emptyList())
    }

    fun reloadEmotes(channel: String, oAuth: String, id: Int) = scope.launch {
        loadedGlobalEmotes = false
        loadedTwitchEmotes = false
        load3rdPartyEmotes(channel)

        if (id != 0 && oAuth.isNotBlank() && oAuth.startsWith("oauth:")) {
            loadTwitchEmotes(oAuth.substringAfter("oauth:"), id)
        }

        setSuggestions(channel)
    }

    fun uploadImage(file: File) = scope.launch {
        val url = TwitchApi.uploadImage(file) ?: "Error during upload"

        imageUploadedEvent.postValue(url to file)
    }

    fun onMessage(msg: IrcMessage, isJustinFan: Boolean): List<ChatItem>? {
        when (msg.command) {
            "366" -> handleConnected(msg.params[1].substring(1), isJustinFan)
            "CLEARCHAT" -> handleClearchat(msg)
            "ROOMSTATE" -> handleRoomstate(msg)
            else -> return handleMessage(msg)
        }
        return null
    }

    private fun handleConnected(channel: String, isJustinFan: Boolean) {
        makeAndPostSystemMessage("Connected", channel)
        hasDisconnected = false

        val hint = if (isJustinFan) "Not logged in" else "Start chatting"
        canType[channel]?.postValue(hint) ?: MutableLiveData(hint)
    }

    private fun handleClearchat(msg: IrcMessage) {
        val parsed = TwitchMessage.parse(msg).map { ChatItem(it) }
        val target = if (msg.params.size > 1) msg.params[1] else ""
        val channel = msg.params[0].substring(1)

        chatLiveDatas[channel]?.value?.replaceWithTimeOuts(target)?.run {
            add(parsed[0])
            chatLiveDatas[channel]?.postValue(this)
        }
    }

    private fun handleRoomstate(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val state = roomStates[channel]?.value
        state?.updateState(msg)

        roomStates[channel]?.postValue(state)

    }

    private fun handleMessage(msg: IrcMessage): List<ChatItem> {
        val parsed = TwitchMessage.parse(msg).map { ChatItem(it) }
        if (parsed.isNotEmpty()) {
            val channel = msg.params[0].substring(1)
            val currentChat = chatLiveDatas[channel]?.value ?: emptyList()
            chatLiveDatas[channel]?.postValue(currentChat.addAndLimit(parsed))
        }

        return parsed
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

    private suspend fun loadTwitchEmotes(oAuth: String, id: Int) = withContext(Dispatchers.IO) {
        if (!loadedTwitchEmotes) {
            TwitchApi.getUserEmotes(oAuth, id)?.let { EmoteManager.setTwitchEmotes(it) }
            loadedTwitchEmotes = true
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
    }

    private fun setSuggestions(channel: String) {
        val keywords = EmoteManager.getEmotesForSuggestions(channel)
        emoteSuggestions[channel]?.postValue(keywords)
    }

    private suspend fun loadRecentMessages(channel: String) = withContext(Dispatchers.Default) {
        val list = mutableListOf<ChatItem>()
        TwitchApi.getRecentMessages(channel)?.messages?.forEach {
            val message = IrcMessage.parse(it)
            val twitchMessage = when (message.tags["display-name"]) {
                "NOTICE" -> TwitchMessage.parseNotice(message)
                "CLEARCHAT" -> TwitchMessage.parseNotice(message)
                "USERNOTICE" -> TwitchMessage.parseUserNotice(message, true)[0]
                else -> TwitchMessage.parseFromIrc(message, isHistoric = true)
            }
            list += ChatItem(twitchMessage, true)
        }

        val current = chatLiveDatas[channel]?.value ?: emptyList()
        chatLiveDatas[channel]?.postValue(list.addAndLimit(current, true))
    }

    companion object {
        private val TAG = TwitchRepository::class.java.simpleName
        private val INVISIBLE_CHAR =
                String(ByteBuffer.allocate(4).putInt(0x000E0000).array(), Charsets.UTF_32)
    }
}