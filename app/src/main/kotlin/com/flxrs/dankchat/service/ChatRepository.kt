package com.flxrs.dankchat.service

import android.util.Log
import androidx.collection.LruCache
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.toMentionTabItems
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.SystemMessageType
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.message.*
import com.flxrs.dankchat.utils.extensions.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import kotlin.collections.set
import kotlin.system.measureTimeMillis

class ChatRepository(private val apiManager: ApiManager, private val emoteManager: EmoteManager) {

    private val _notificationsFlow = MutableSharedFlow<List<ChatItem>>(0, extraBufferCapacity = 10)
    private val _channelMentionCount = MutableSharedFlow<MutableMap<String, Int>>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply { tryEmit(mutableMapOf()) }
    private val messages = mutableMapOf<String, MutableStateFlow<List<ChatItem>>>()
    private val _mentions = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _whispers = MutableStateFlow<List<ChatItem>>(emptyList())
    private val connectionState = mutableMapOf<String, MutableStateFlow<SystemMessageType>>()
    private val roomStates = mutableMapOf<String, MutableStateFlow<Roomstate>>()
    private val users = mutableMapOf<String, MutableStateFlow<LruCache<String, Boolean>>>()
    private val ignoredList = mutableListOf<Int>()

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(MultiEntryItem.Entry::class.java)

    private val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    private val request = Request.Builder().url("wss://irc-ws.chat.twitch.tv").build()
    private val readConnection = WebSocketConnection("reader", CoroutineScope(Dispatchers.IO + Job()), client, request, ::handleDisconnect, ::onReaderMessage)
    private val writeConnection = WebSocketConnection("writer", CoroutineScope(Dispatchers.IO + Job()), client, request, null, ::onWriterMessage)

    private var hasDisconnected = true
    private var customMentionEntries = listOf<Mention>()
    private var blacklistEntries = listOf<Mention>()
    private var name: String = ""

    val notificationsFlow: SharedFlow<List<ChatItem>> = _notificationsFlow.asSharedFlow()
    val channelMentionCount: SharedFlow<Map<String, Int>> = _channelMentionCount.asSharedFlow()
    val mentions: StateFlow<List<ChatItem>>
        get() = _mentions
    val whispers: StateFlow<List<ChatItem>>
        get() = _whispers

    var startedConnection = false
    var lastMessage = mutableMapOf<String, String>()
    var scrollbackLength = 500
        set(value) {
            messages.forEach { (_, messagesFlow) ->
                if (messagesFlow.value.size > scrollbackLength) {
                    messagesFlow.value = messagesFlow.value.takeLast(value)
                }
            }
            field = value
        }

    fun getChat(channel: String): StateFlow<List<ChatItem>> = messages.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun getConnectionState(channel: String): StateFlow<SystemMessageType> = connectionState.getOrPut(channel) { MutableStateFlow(SystemMessageType.DISCONNECTED) }
    fun getRoomState(channel: String): StateFlow<Roomstate> = roomStates.getOrPut(channel) { MutableStateFlow(Roomstate(channel)) }
    fun getUsers(channel: String): StateFlow<LruCache<String, Boolean>> = users.getOrPut(channel) { MutableStateFlow(createUserCache()) }

    suspend fun loadRecentMessages(channel: String, loadHistory: Boolean) {
        if (loadHistory) {
            loadRecentMessages(channel)
        } else {
            val currentChat = messages[channel]?.value ?: emptyList()
            messages[channel]?.value = listOf(ChatItem(SystemMessage(state = SystemMessageType.NO_HISTORY_LOADED), false)) + currentChat
        }
    }

    suspend fun loadIgnores(oAuth: String, id: String) {
        if (oAuth.isNotBlank()) {
            apiManager.getIgnores(oAuth, id)?.let { (blocks) ->
                ignoredList.clear()
                ignoredList.addAll(blocks.map { it.user.id })
            }
        }
    }

    suspend fun loadChatters(channel: String) = withContext(Dispatchers.Default) {
        if (!users.contains(channel)) users[channel] = MutableStateFlow(createUserCache())

        measureTimeMillis {
            apiManager.getChatters(channel)?.let { chatters ->
                users[channel]?.value?.let { cache ->
                    val size = chatters.total.size
                    if (size > USER_CACHE_SIZE) {
                        Log.i(TAG, "Resizing user cache for #$channel to $size")
                        cache.resize(size)
                    }

                    chatters.total.forEach { cache.put(it, true) }
                    users[channel]?.value = cache
                }

            }
        }.let { Log.i(TAG, "Loading chatters for #$channel took $it ms") }
    }

    fun removeChannelData(channel: String) {
        messages[channel]?.value = emptyList()
        messages.remove(channel)
        lastMessage.remove(channel)
        _channelMentionCount.firstValue.remove(channel)
        apiManager.clearChannelFromLoaded(channel)
    }

    fun clearMentionCount(channel: String) = with(_channelMentionCount) {
        tryEmit(firstValue.apply { set(channel, 0) })
    }

    fun clearMentionCounts() = with(_channelMentionCount) {
        tryEmit(firstValue.apply { keys.forEach { if (it != "w") set(it, 0) } })
    }

    fun clear(channel: String) {
        messages[channel]?.value = emptyList()
    }

    fun close(onClosed: () -> Unit = { }): Boolean {
        startedConnection = false
        return writeConnection.close(onClosed) && readConnection.close(onClosed)
    }

    fun reconnect(onlyIfNecessary: Boolean) {
        readConnection.reconnect(onlyIfNecessary)
        writeConnection.reconnect(onlyIfNecessary)
    }

    fun sendMessage(channel: String, input: String) {
        prepareMessage(channel, input, writeConnection::sendMessage)

        val split = input.split(" ")
        if (split.size > 2 && split[0] == "/w" && split[1].isNotBlank()) {
            val message = input.substring(4 + split[1].length)
            val emotes = emoteManager.parse3rdPartyEmotes(message, withTwitch = true)
            val fakeMessage = TwitchMessage(channel = "", name = name, displayName = name, message = message, emotes = emotes, isWhisper = true, whisperRecipient = split[1])
            val fakeItem = ChatItem(fakeMessage, isMentionTab = true)
            _whispers.value = _whispers.value.addAndLimit(fakeItem, scrollbackLength)
        }
    }

    fun connect(nick: String, oauth: String, forceConnect: Boolean = false) {
        if (!startedConnection) {
            name = nick
            readConnection.connect(nick, oauth, forceConnect)
            writeConnection.connect(nick, oauth, forceConnect)
            startedConnection = true
        }
    }

    fun joinChannel(channel: String) {
        createFlowsIfNecessary(channel)
        readConnection.joinChannel(channel)
        writeConnection.joinChannel(channel)
    }

    fun partChannel(channel: String) {
        readConnection.partChannel(channel)
        writeConnection.partChannel(channel)
    }

    private fun createFlowsIfNecessary(channel: String) {
        messages.putIfAbsent(channel, MutableStateFlow(emptyList()))
        connectionState.putIfAbsent(channel, MutableStateFlow(SystemMessageType.DISCONNECTED))
        roomStates.putIfAbsent(channel, MutableStateFlow(Roomstate(channel)))
        users.putIfAbsent(channel, MutableStateFlow(createUserCache()))

        with(_channelMentionCount) {
            if (!firstValue.contains("w")) tryEmit(firstValue.apply { set(channel, 0) })
            if (!firstValue.contains(channel)) tryEmit(firstValue.apply { set(channel, 0) })
        }
    }

    private inline fun prepareMessage(channel: String, message: String, onResult: (msg: String) -> Unit) {
        if (message.isNotBlank()) {
            val messageWithSuffix = when (lastMessage[channel] ?: "") {
                message -> "$message $INVISIBLE_CHAR"
                else -> message
            }

            onResult("PRIVMSG #$channel :$messageWithSuffix")
        }
    }

    private fun onWriterMessage(message: IrcMessage) {
        if (message.isLoginFailed()) {
            startedConnection = false
        }
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
            "CLEARCHAT" -> handleClearchat(msg)
            "ROOMSTATE" -> handleRoomstate(msg)
            "CLEARMSG" -> handleClearmsg(msg)
            else -> handleMessage(msg)
        }
        return null
    }

    private fun handleDisconnect() {
        if (!hasDisconnected) {
            hasDisconnected = true
            val state = SystemMessageType.DISCONNECTED
            connectionState.keys.forEach {
                connectionState[it]?.value = state
            }
            makeAndPostConnectionMessage(state)
        }
    }

    fun clearIgnores() {
        ignoredList.clear()
    }

    suspend fun setMentionEntries(stringSet: Set<String>?) = withContext(Dispatchers.Default) {
        customMentionEntries = stringSet.mapToMention(adapter)
    }

    suspend fun setBlacklistEntries(stringSet: Set<String>?) = withContext(Dispatchers.Default) {
        blacklistEntries = stringSet.mapToMention(adapter)
    }

    private fun handleConnected(channel: String, isAnonymous: Boolean) {
        hasDisconnected = false

        val connection = when {
            isAnonymous -> SystemMessageType.NOT_LOGGED_IN
            else -> SystemMessageType.CONNECTED
        }
        makeAndPostConnectionMessage(connection, setOf(channel))
        connectionState[channel]?.value = connection
    }

    private fun handleClearchat(msg: IrcMessage) {
        val parsed = TwitchMessage.parse(msg, emoteManager).map { ChatItem(it) }
        val target = if (msg.params.size > 1) msg.params[1] else ""
        val channel = msg.params[0].substring(1)

        messages[channel]?.value?.replaceWithTimeOuts(target)?.also {
            messages[channel]?.value = it.addAndLimit(parsed[0], scrollbackLength)
        }
    }

    private fun handleRoomstate(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val state = roomStates[channel]?.value ?: Roomstate(channel)
        state.updateState(msg)

        roomStates[channel]?.value = state
    }

    private fun handleClearmsg(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val targetId = msg.tags["target-msg-id"] ?: return

        messages[channel]?.value?.replaceWithTimeOut(targetId)?.also {
            messages[channel]?.value = it
        }
    }

    private fun handleMessage(msg: IrcMessage) {
        msg.tags["user-id"]?.let { userId ->
            if (ignoredList.any { it == userId.toInt() }) return
        }
        val parsed = TwitchMessage.parse(msg, emoteManager).map {
            if (it.name == name) lastMessage[it.channel] = it.message
            if (blacklistEntries.matches(it.message, it.name to it.displayName, it.emotes)) return

            it.checkForMention(name, customMentionEntries)
            val currentUsers = users[it.channel]?.value ?: createUserCache()
            currentUsers.put(it.name, true)
            users[it.channel]?.value = currentUsers

            ChatItem(it)
        }
        if (parsed.isNotEmpty()) {
            if (msg.params[0] == "*") {
                messages.keys.forEach {
                    val currentChat = messages[it]?.value ?: emptyList()
                    messages[it]?.value = currentChat.addAndLimit(parsed, scrollbackLength)
                }
            } else {
                val channel = msg.params[0].substring(1)
                val currentChat = messages[channel]?.value ?: emptyList()
                messages[channel]?.value = currentChat.addAndLimit(parsed, scrollbackLength)
                _notificationsFlow.tryEmit(parsed)

                if (msg.command == "WHISPER") {
                    (parsed[0].message as TwitchMessage).whisperRecipient = name
                    _whispers.value = _whispers.value.addAndLimit(parsed.toMentionTabItems(), scrollbackLength)
                    _channelMentionCount.increment("w", 1)
                }

                val mentions = parsed.filter { it.message is TwitchMessage && it.message.isMention && !it.message.isWhisper }.toMentionTabItems()
                if (mentions.isNotEmpty()) {
                    _channelMentionCount.increment(channel, mentions.size)
                    _mentions.value = _mentions.value.addAndLimit(mentions, scrollbackLength)
                }
            }
        }
    }

    private fun createUserCache(): LruCache<String, Boolean> {
        return object : LruCache<String, Boolean>(USER_CACHE_SIZE) {
            override fun equals(other: Any?): Boolean = false
        }
    }

    private fun makeAndPostConnectionMessage(state: SystemMessageType, channels: Set<String> = messages.keys) {
        channels.forEach {
            val currentChat = messages[it]?.value ?: emptyList()
            messages[it]?.value = currentChat.addAndLimit(ChatItem(SystemMessage(state = state)), scrollbackLength)
        }
    }

    private suspend fun loadRecentMessages(channel: String) = withContext(Dispatchers.Default) {
        val result = apiManager.getRecentMessages(channel) ?: return@withContext
        val items = mutableListOf<ChatItem>()
        measureTimeMillis {
            for (message in result.messages) {
                val parsedIrc = IrcMessage.parse(message)
                if (ignoredList.any { parsedIrc.tags["user-id"]?.toInt() == it }) continue

                for (msg in TwitchMessage.parse(parsedIrc, emoteManager)) {
                    if (!blacklistEntries.matches(msg.message, msg.name to msg.displayName, msg.emotes)) {
                        msg.checkForMention(name, customMentionEntries)
                        items += ChatItem(msg)
                    }
                }
            }
        }.let { Log.i(TAG, "Parsing message history for #$channel took $it ms") }

        val current = messages[channel]?.value ?: emptyList()
        messages[channel]?.value = items.addAndLimit(current, scrollbackLength, checkForDuplications = true)

        val mentions = items.filter { (it.message as TwitchMessage).isMention }.toMentionTabItems()
        _mentions.value = (_mentions.value + mentions).sortedBy { it.message.timestamp }
    }

    companion object {
        private val TAG = ChatRepository::class.java.simpleName
        private val INVISIBLE_CHAR = String(ByteBuffer.allocate(4).putInt(0x000E0000).array(), Charsets.UTF_32)
        private const val USER_CACHE_SIZE = 500
        const val MENTIONS_KEY = "mentions"
    }
}