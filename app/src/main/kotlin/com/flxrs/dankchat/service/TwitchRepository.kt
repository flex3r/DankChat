package com.flxrs.dankchat.service

import android.util.Log
import androidx.collection.LruCache
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.emote.GenericEmote
import com.flxrs.dankchat.service.twitch.message.Mention
import com.flxrs.dankchat.service.twitch.message.Message
import com.flxrs.dankchat.service.twitch.message.Roomstate
import com.flxrs.dankchat.service.twitch.message.matches
import com.flxrs.dankchat.utils.SingleLiveEvent
import com.flxrs.dankchat.utils.extensions.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.parameter.parametersOf
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.system.measureTimeMillis

class TwitchRepository(private val scope: CoroutineScope) : KoinComponent {

    private val messages = mutableMapOf<String, MutableLiveData<List<ChatItem>>>()
    private val emotes = mutableMapOf<String, MutableLiveData<List<GenericEmote>>>()
    private val connectionState = mutableMapOf<String, MutableLiveData<SystemMessageType>>()
    private val roomStates = mutableMapOf<String, MutableLiveData<Roomstate>>()
    private val users = mutableMapOf<String, MutableLiveData<LruCache<String, Boolean>>>()
    private val ignoredList = mutableListOf<Int>()
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
    }

    private var hasDisconnected = true
    private var loadedGlobalBadges = false
    private var loadedGlobalEmotes = false
    private var loadedTwitchEmotes = false
    private var lastMessage = ""

    private var name: String = ""
    private var customMentionEntries = listOf<Mention>()
    private var blacklistEntries = listOf<Mention>()
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(MultiEntryItem.Entry::class.java)

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .build()

    private val request = Request.Builder()
        .url("wss://irc-ws.chat.twitch.tv")
        .build()

    private val readConnection: WebSocketConnection = get { parametersOf(client, request, ::handleDisconnect, ::onReaderMessage) }
    private val writeConnection: WebSocketConnection = get { parametersOf(client, request, null, ::onWriterMessage) }

    val imageUploadedEvent = SingleLiveEvent<Pair<String?, File>>()
    val messageChannel = Channel<List<ChatItem>>()
    var startedConnection = false

    fun getChat(channel: String): LiveData<List<ChatItem>> = messages.getAndSet(channel, emptyList())

    fun getConnectionState(channel: String): LiveData<SystemMessageType> = connectionState.getAndSet(channel, SystemMessageType.DISCONNECTED)

    fun getEmotes(channel: String): LiveData<List<GenericEmote>> = emotes.getAndSet(channel, emptyList())

    fun getRoomState(channel: String): LiveData<Roomstate> = roomStates.getAndSet(channel, Roomstate(channel))

    fun getUsers(channel: String): LiveData<LruCache<String, Boolean>> = users.getAndSet(channel, createUserCache())

    fun loadData(channels: List<String>, oAuth: String, id: Int, loadTwitchData: Boolean, loadHistory: Boolean, name: String) {
        this.name = name
        scope.launch(coroutineExceptionHandler) {
            ConcurrentLinkedQueue(channels).forEach { channel ->
                TwitchApi.getUserIdFromName(oAuth, channel)?.let {
                    loadBadges(channel, it)
                    load3rdPartyEmotes(channel, it)
                }
                if (oAuth.isNotBlank() && loadTwitchData && channel == channels.first()) {
                    loadedTwitchEmotes = false
                    loadIgnores(oAuth, id)
                    loadTwitchEmotes(oAuth, id)
                }

                setSuggestions(channel)

                if (loadHistory) {
                    loadRecentMessages(channel)
                } else {
                    val currentChat = messages[channel]?.value ?: emptyList()
                    messages.getAndSet(channel).postValue(
                        listOf(ChatItem(Message.SystemMessage(state = SystemMessageType.NO_HISTORY_LOADED), false)).plus(currentChat)
                    )
                }
            }
        }
    }

    fun removeChannelData(channel: String) {
        messages[channel]?.postValue(emptyList())
        messages.remove(channel)
        TwitchApi.clearChannelFromLoaded(channel)
    }

    @Synchronized
    fun handleDisconnect() {
        if (!hasDisconnected) {
            hasDisconnected = true
            val state = SystemMessageType.DISCONNECTED
            connectionState.keys.forEach {
                connectionState.getAndSet(it).postValue(state)
            }
            makeAndPostConnectionMessage(state)
        }
    }

    fun clear(channel: String) {
        messages[channel]?.postValue(emptyList())
    }

    fun reloadEmotes(channel: String, oAuth: String, id: Int) =
        scope.launch(coroutineExceptionHandler) {
            loadedGlobalEmotes = false
            loadedTwitchEmotes = false
            TwitchApi.getUserIdFromName(oAuth, channel)?.let {
                load3rdPartyEmotes(channel, it)
            }

            if (id != 0 && oAuth.isNotBlank() && oAuth.startsWith("oauth:")) {
                loadTwitchEmotes(oAuth.substringAfter("oauth:"), id)
            }

            setSuggestions(channel)
        }

    fun uploadImage(file: File) = scope.launch(coroutineExceptionHandler) {
        val url = TwitchApi.uploadImage(file)
        imageUploadedEvent.postValue(url to file)
    }

    fun close(onClosed: () -> Unit = { }) {
        startedConnection = false
        writeConnection.close(onClosed)
        readConnection.close(onClosed)
    }

    fun reconnect(onlyIfNecessary: Boolean) {
        readConnection.reconnect(onlyIfNecessary)
        writeConnection.reconnect(onlyIfNecessary)
    }

    fun sendMessage(channel: String, input: String) = prepareMessage(channel, input) {
        writeConnection.sendMessage(it)
    }

    fun connect(nick: String, oauth: String) {
        if (!startedConnection) {
            readConnection.connect(nick, oauth)
            writeConnection.connect(nick, oauth)
            startedConnection = true
        }
    }

    fun joinChannel(channel: String) {
        readConnection.joinChannel(channel)
        writeConnection.joinChannel(channel)
    }

    fun partChannel(channel: String) {
        readConnection.partChannel(channel)
        writeConnection.partChannel(channel)
    }

    private inline fun prepareMessage(channel: String, message: String, onResult: (msg: String) -> Unit) {
        if (message.isNotBlank()) {
            val messageWithSuffix = when (lastMessage) {
                message -> "$message $INVISIBLE_CHAR"
                else -> message
            }

            lastMessage = messageWithSuffix
            onResult("PRIVMSG #$channel :$messageWithSuffix")
        }
    }

    private fun onWriterMessage(message: IrcMessage) {
        when (message.command) {
            "PRIVMSG" -> Unit
            "366" -> handleConnected(message.params[1].substring(1), writeConnection.isAnonymous)
            else -> onMessage(message)
        }
    }

    private fun onReaderMessage(message: IrcMessage) {
        if (message.command == "PRIVMSG") {
            onMessage(message)
        }
    }

    private fun onMessage(msg: IrcMessage): List<ChatItem>? {
        when (msg.command) {
            "353" -> handleNames(msg)
            "JOIN" -> handleJoin(msg)
            "CLEARCHAT" -> handleClearchat(msg)
            "ROOMSTATE" -> handleRoomstate(msg)
            "CLEARMSG" -> handleClearmsg(msg)
            else -> handleMessage(msg)
        }
        return null
    }

    fun clearIgnores() {
        ignoredList.clear()
    }

    fun setMentionEntries(stringSet: Set<String>?) {
        scope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            customMentionEntries = stringSet.mapToMention(adapter)
        }
    }

    fun setBlacklistEntries(stringSet: Set<String>?) {
        scope.launch(Dispatchers.Default + coroutineExceptionHandler) {
            blacklistEntries = stringSet.mapToMention(adapter)
        }
    }

    private suspend fun loadIgnores(oAuth: String, id: Int) = withContext(Dispatchers.Default) {
        val result = TwitchApi.getIgnores(oAuth, id)
        if (result != null) {
            ignoredList.clear()
            ignoredList.addAll(result.blocks.map { it.user.id })
        }
    }

    private fun handleConnected(channel: String, isAnonymous: Boolean) {
        hasDisconnected = false

        val hint = when {
            isAnonymous -> SystemMessageType.NOT_LOGGED_IN
            else -> SystemMessageType.CONNECTED
        }
        makeAndPostConnectionMessage(hint, setOf(channel))
        connectionState.getAndSet(channel).postValue(hint)
    }

    private fun handleClearchat(msg: IrcMessage) {
        val parsed = Message.TwitchMessage.parse(msg).map { ChatItem(it) }
        val target = if (msg.params.size > 1) msg.params[1] else ""
        val channel = msg.params[0].substring(1)

        messages[channel]?.value?.replaceWithTimeOuts(target)?.also {
            messages[channel]?.postValue(it.addAndLimit(parsed[0]))
        }
    }

    private fun handleRoomstate(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val state = roomStates[channel]?.value ?: Roomstate(channel)
        state.updateState(msg)

        roomStates.getAndSet(channel).postValue(state)
    }

    private fun handleClearmsg(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val targetId = msg.tags["target-msg-id"] ?: return

        messages[channel]?.value?.replaceWithTimeOut(targetId)?.also {
            messages[channel]?.postValue(it)
        }
    }

    private fun handleMessage(msg: IrcMessage) {
        msg.tags["user-id"]?.let { userId ->
            if (ignoredList.any { it == userId.toInt() }) return
        }
        val parsed = Message.TwitchMessage.parse(msg).map {
            if (blacklistEntries.matches(it.message, it.name to it.displayName, it.emotes)) return

            it.checkForMention(name, customMentionEntries)
            val currentUsers = users[it.channel]?.value ?: createUserCache()
            currentUsers.put(it.name, true)
            users.getAndSet(it.channel).postValue(currentUsers)

            ChatItem(it)
        }
        if (parsed.isNotEmpty()) {
            if (msg.params[0] == "*" || msg.command == "WHISPER") {
                messages.forEach {
                    val currentChat = it.value.value ?: emptyList()
                    messages.getAndSet(it.key).postValue(currentChat.addAndLimit(parsed))
                }
            } else {
                val channel = msg.params[0].substring(1)
                val currentChat = messages[channel]?.value ?: emptyList()
                messages.getAndSet(channel).postValue(currentChat.addAndLimit(parsed))
                messageChannel.offer(parsed)
            }
        }
    }

    private fun handleNames(msg: IrcMessage) {
        val channel = msg.params.getOrNull(2)?.substring(1) ?: return
        val names = msg.params.getOrNull(3)?.split(' ') ?: return
        val currentUsers = users[channel]?.value ?: createUserCache()
        names.forEach { currentUsers.put(it, true) }
        users.getAndSet(channel).postValue(currentUsers)
    }

    private fun handleJoin(msg: IrcMessage) {
        val channel = msg.params.getOrNull(0)?.substring(1) ?: return
        val name = msg.prefix.substringBefore('!')
        val currentUsers = users[channel]?.value ?: createUserCache()
        currentUsers.put(name, true)
        users.getAndSet(channel).postValue(currentUsers)
    }

    private fun createUserCache(): LruCache<String, Boolean> {
        return LruCache(500)
    }

    private fun makeAndPostConnectionMessage(state: SystemMessageType, channels: Set<String> = messages.keys) {
        channels.forEach {
            val currentChat = messages[it]?.value ?: emptyList()
            messages.getAndSet(it).postValue(
                currentChat.addAndLimit(ChatItem(Message.SystemMessage(state = state)))
            )
        }
    }

    private suspend fun loadBadges(channel: String, id: String) = withContext(Dispatchers.Default) {
        if (!loadedGlobalBadges) {
            loadedGlobalBadges = true
            TwitchApi.getGlobalBadges()?.let { EmoteManager.setGlobalBadges(it) }
        }
        TwitchApi.getChannelBadges(id)?.let { EmoteManager.setChannelBadges(channel, it) }
    }

    private suspend fun loadTwitchEmotes(oAuth: String, id: Int) = withContext(Dispatchers.Default) {
        if (!loadedTwitchEmotes) {
            TwitchApi.getUserEmotes(oAuth, id)?.let { EmoteManager.setTwitchEmotes(it) }
            loadedTwitchEmotes = true
        }
    }

    private suspend fun load3rdPartyEmotes(channel: String, id: String) = withContext(Dispatchers.IO) {
        TwitchApi.getFFZChannelEmotes(id)?.let { EmoteManager.setFFZEmotes(channel, it) }
        TwitchApi.getBTTVChannelEmotes(id)?.let { EmoteManager.setBTTVEmotes(channel, it) }

        if (!loadedGlobalEmotes) {
            TwitchApi.getFFZGlobalEmotes()?.let { EmoteManager.setFFZGlobalEmotes(it) }
            TwitchApi.getBTTVGlobalEmotes()?.let { EmoteManager.setBTTVGlobalEmotes(it) }
            loadedGlobalEmotes = true
        }
    }

    private suspend fun setSuggestions(channel: String) = withContext(Dispatchers.Default) {
        emotes.getAndSet(channel).postValue(EmoteManager.getEmotes(channel))
    }

    private suspend fun loadRecentMessages(channel: String): Unit? = withContext(Dispatchers.Default) {
        val result = TwitchApi.getRecentMessages(channel) ?: return@withContext
        val items = mutableListOf<ChatItem>()
        measureTimeMillis {
            for (message in result.messages) {
                val parsedIrc = IrcMessage.parse(message)
                if (ignoredList.any { parsedIrc.tags["user-id"]?.toInt() == it }) continue

                for (msg in Message.TwitchMessage.parse(parsedIrc)) {
                    if (!blacklistEntries.matches(msg.message, msg.name to msg.displayName, msg.emotes)) {
                        items.add(ChatItem(msg))
                    }
                }
            }
        }.let { Log.i(TAG, "Parsing message history for #$channel took $it ms") }

        val current = messages[channel]?.value ?: emptyList()
        messages.getAndSet(channel).postValue(items.addAndLimit(current, true))
    }

    companion object {
        private val TAG = TwitchRepository::class.java.simpleName
        private val INVISIBLE_CHAR =
            String(ByteBuffer.allocate(4).putInt(0x000E0000).array(), Charsets.UTF_32)
    }
}