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

    fun getChat(channel: String): LiveData<List<ChatItem>> = chatLiveDatas.getOrPut(channel) {
        MutableLiveData(emptyList())
    }

    fun getCanType(channel: String): LiveData<String> = canType.getOrPut(channel) {
        MutableLiveData("Disconnected")
    }

    fun getEmoteKeywords(channel: String): LiveData<List<GenericEmote>> =
        emoteSuggestions.getOrPut(channel) {
            MutableLiveData(emptyList())
        }

    fun getRoomState(channel: String): LiveData<TwitchMessage.Roomstate> =
        roomStates.getOrPut(channel) {
            MutableLiveData(TwitchMessage.Roomstate(channel))
        }

    fun loadData(channel: String, oAuth: String, id: Int, load3rdParty: Boolean, reAuth: Boolean) {
        if (reAuth) {
            loadedTwitchEmotes = false
        }

        scope.launch {
            if (load3rdParty) {
                TwitchApi.getUserIdFromName(channel)?.let {
                    loadBadges(channel, it)
                    load3rdPartyEmotes(channel, it)
                }
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
            val messageWithSuffix =
                if (lastMessage == message) "$message $INVISIBLE_CHAR" else message
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
        TwitchApi.getUserIdFromName(channel)?.let {
            load3rdPartyEmotes(channel, it)
        }

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
        makeAndPostSystemMessage("Connected", setOf(channel))
        hasDisconnected = false

        val hint = if (isJustinFan) "Not logged in" else "Start chatting"
        canType[channel]?.postValue(hint) ?: MutableLiveData(hint)
    }

    private fun handleClearchat(msg: IrcMessage) {
        val parsed = TwitchMessage.parse(msg).map { ChatItem(it) }
        val target = if (msg.params.size > 1) msg.params[1] else ""
        val channel = msg.params[0].substring(1)

        chatLiveDatas[channel]?.value?.replaceWithTimeOuts(target)?.run {
            chatLiveDatas[channel]?.postValue(addAndLimit(parsed[0]))
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

    private fun makeAndPostSystemMessage(
        message: String,
        channels: Set<String> = chatLiveDatas.keys
    ) {
        channels.forEach {
            val currentChat = chatLiveDatas[it]?.value ?: emptyList()
            chatLiveDatas[it]?.postValue(
                currentChat.addAndLimit(ChatItem(TwitchMessage.makeSystemMessage(message, it)))
            )
        }
    }

    private suspend fun loadBadges(channel: String, id: String) = withContext(Dispatchers.IO) {
        if (!loadedGlobalBadges) TwitchApi.getGlobalBadges()?.let {
            EmoteManager.setGlobalBadges(it)
            loadedGlobalBadges = true
        }
        TwitchApi.getChannelBadges(id)?.let { EmoteManager.setChannelBadges(channel, it) }
    }

    private suspend fun loadTwitchEmotes(oAuth: String, id: Int) = withContext(Dispatchers.IO) {
        if (!loadedTwitchEmotes) {
            TwitchApi.getUserEmotes(oAuth, id)?.let { EmoteManager.setTwitchEmotes(it) }
            loadedTwitchEmotes = true
        }
    }

    private suspend fun load3rdPartyEmotes(channel: String, id: String) =
        withContext(Dispatchers.IO) {
            TwitchApi.getFFZChannelEmotes(id)?.let { EmoteManager.setFFZEmotes(channel, it) }
            TwitchApi.getBTTVChannelEmotes(id)?.let { EmoteManager.setBTTVEmotes(channel, it) }

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
        TwitchApi.getRecentMessages(channel)
            ?.messages?.map { TwitchMessage.parse(IrcMessage.parse(it)) }
            ?.flatten()
            ?.map { ChatItem(it) }
            ?.let {
                val current = chatLiveDatas[channel]?.value ?: emptyList()
                chatLiveDatas[channel]?.postValue(it.addAndLimit(current, true))
            }
    }

    companion object {
        private val INVISIBLE_CHAR =
            String(ByteBuffer.allocate(4).putInt(0x000E0000).array(), Charsets.UTF_32)
    }
}