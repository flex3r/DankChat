package com.flxrs.dankchat.service

import android.util.Log
import androidx.collection.LruCache
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.toMentionTabItems
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.ConnectionState
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.message.*
import com.flxrs.dankchat.utils.extensions.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import kotlin.collections.set
import kotlin.system.measureTimeMillis

class ChatRepository(private val apiManager: ApiManager, private val emoteManager: EmoteManager) {

    data class UserState(val userId: String = "", val color: String? = null, val displayName: String = "", val emoteSets: List<String> = listOf())

    private val _activeChannel = MutableStateFlow("")
    private val _channels = MutableStateFlow<List<String>?>(null)


    private val _notificationsFlow = MutableSharedFlow<List<ChatItem>>(0, extraBufferCapacity = 10)
    private val _channelMentionCount = mutableSharedFlowOf(mutableMapOf<String, Int>())
    private val messages = mutableMapOf<String, MutableStateFlow<List<ChatItem>>>()
    private val _mentions = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _whispers = MutableStateFlow<List<ChatItem>>(emptyList())
    private val connectionState = mutableMapOf<String, MutableStateFlow<ConnectionState>>()
    private val roomStates = mutableMapOf<String, MutableSharedFlow<RoomState>>()
    private val users = mutableMapOf<String, MutableStateFlow<LruCache<String, Boolean>>>()
    private val _userState = MutableStateFlow(UserState())
    private val blockList = mutableSetOf<String>()

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
    private var lastMessage = mutableMapOf<String, String>()
    private var startedConnection = false

    val notificationsFlow: SharedFlow<List<ChatItem>> = _notificationsFlow.asSharedFlow()
    val channelMentionCount: SharedFlow<Map<String, Int>> = _channelMentionCount.asSharedFlow()
    val hasMentions = channelMentionCount.map { it.any { channel -> channel.key != "w" && channel.value > 0 } }
    val hasWhispers = channelMentionCount.map { it.getOrDefault("w", 0) > 0 }
    val mentions: StateFlow<List<ChatItem>>
        get() = _mentions
    val whispers: StateFlow<List<ChatItem>>
        get() = _whispers
    val activeChannel: StateFlow<String>
        get() = _activeChannel.asStateFlow()
    val channels: StateFlow<List<String>?>
        get() = _channels.asStateFlow()

    val userState: StateFlow<UserState>
        get() = _userState.asStateFlow()

    var scrollBackLength = 500
        set(value) {
            messages.forEach { (_, messagesFlow) ->
                if (messagesFlow.value.size > scrollBackLength) {
                    messagesFlow.value = messagesFlow.value.takeLast(value)
                }
            }
            field = value
        }

    fun getChat(channel: String): StateFlow<List<ChatItem>> = messages.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun getConnectionState(channel: String): StateFlow<ConnectionState> = connectionState.getOrPut(channel) { MutableStateFlow(ConnectionState.DISCONNECTED) }
    fun getRoomState(channel: String): SharedFlow<RoomState> = roomStates.getOrPut(channel) { mutableSharedFlowOf(RoomState(channel)) }
    fun getUsers(channel: String): StateFlow<LruCache<String, Boolean>> = users.getOrPut(channel) { MutableStateFlow(createUserCache()) }
    suspend fun getLatestValidUserState(): UserState = userState.filter { it.userId.isNotBlank() }.take(count = 1).single()

    suspend fun loadRecentMessages(channel: String, loadHistory: Boolean, isUserChange: Boolean) {
        when {
            isUserChange -> return
            loadHistory  -> loadRecentMessages(channel)
            else         -> {
                val currentChat = messages[channel]?.value ?: emptyList()
                messages[channel]?.value = listOf(ChatItem(SystemMessage(type = SystemMessageType.NO_HISTORY_LOADED), false)) + currentChat
            }
        }
    }

    suspend fun loadUserBlocks(oAuth: String, id: String) {
        if (oAuth.isNotBlank()) {
            apiManager.getUserBlocks(oAuth, id)?.let { (data) ->
                blockList.clear()
                blockList.addAll(data.map { it.id })
            }
        }
    }

    fun isUserBlocked(targetUserId: String): Boolean = targetUserId in blockList
    fun addUserBlock(targetUserId: String) = blockList.add(targetUserId)
    fun removeUserBlock(targetUserId: String) = blockList.remove(targetUserId)

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

    fun setActiveChannel(channel: String) {
        _activeChannel.value = channel
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

    fun closeAndReconnect(name: String, oAuth: String) {
        val channels = channels.value.orEmpty()
        startedConnection = false
        val onClosed = { connectAndJoin(name, oAuth, channels) }

        val didClose = writeConnection.close(onClosed) && readConnection.close(onClosed)
        if (!didClose) {
            connectAndJoin(name, oAuth, channels, forceConnect = true)
        }
    }

    fun reconnect(onlyIfNecessary: Boolean) {
        readConnection.reconnect(onlyIfNecessary)
        writeConnection.reconnect(onlyIfNecessary)
    }

    fun getLastMessage(): String? = lastMessage[activeChannel.value]?.trimEndSpecialChar()

    fun sendMessage(input: String) {
        val channel = activeChannel.value
        prepareMessage(channel, input, writeConnection::sendMessage)

        val split = input.split(" ")
        if (split.size > 2 && split[0] == "/w" && split[1].isNotBlank()) {
            val message = input.substring(4 + split[1].length)
            val emotes = emoteManager.parse3rdPartyEmotes(message, withTwitch = true)
            val fakeMessage = TwitchMessage(channel = "", name = name, displayName = name, message = message, emotes = emotes, isWhisper = true, whisperRecipient = split[1])
            val fakeItem = ChatItem(fakeMessage, isMentionTab = true)
            _whispers.value = _whispers.value.addAndLimit(fakeItem, scrollBackLength)
        }
    }

    fun connectAndJoin(name: String, oAuth: String, channels: List<String>, forceConnect: Boolean = false) {
        if (startedConnection) return

        connect(name, oAuth, forceConnect)
        joinChannels(channels)
    }

    fun joinChannel(channel: String): List<String> {
        val channels = channels.value.orEmpty()
        if (channel in channels)
            return channels

        val updatedChannels = channels + channel
        _channels.value = updatedChannels

        createFlowsIfNecessary(channel)
        messages[channel]?.value = emptyList()


        readConnection.joinChannel(channel)
        writeConnection.joinChannel(channel)

        return updatedChannels
    }

    fun partChannel(channel: String): List<String> {
        val updatedChannels = channels.value.orEmpty() - channel
        _channels.value = updatedChannels

        removeChannelData(channel)
        readConnection.partChannel(channel)
        writeConnection.partChannel(channel)

        return updatedChannels
    }

    fun updateChannels(updatedChannels: List<String>) {
        val currentChannels = channels.value.orEmpty()
        val removedChannels = currentChannels - updatedChannels

        removedChannels.forEach {
            partChannel(it)
        }

        _channels.value = updatedChannels
    }

    private fun connect(nick: String, oauth: String, forceConnect: Boolean = false) {
        if (!startedConnection) {
            name = nick
            readConnection.connect(nick, oauth, forceConnect)
            writeConnection.connect(nick, oauth, forceConnect)
            startedConnection = true
        }
    }

    private fun joinChannels(channels: List<String>) {
        _channels.value = channels
        if (channels.isEmpty()) return

        channels.onEach {
            createFlowsIfNecessary(it)
            if (messages[it]?.value == null) {
                messages[it]?.value = emptyList()
            }
        }

        readConnection.joinChannels(channels)
        writeConnection.joinChannels(channels)
    }

    private fun removeChannelData(channel: String) {
        messages[channel]?.value = emptyList()
        lastMessage.remove(channel)
        _channelMentionCount.clear(channel)
        apiManager.clearChannelFromLoaded(channel)
    }

    private fun createFlowsIfNecessary(channel: String) {
        messages.putIfAbsent(channel, MutableStateFlow(emptyList()))
        connectionState.putIfAbsent(channel, MutableStateFlow(ConnectionState.DISCONNECTED))
        roomStates.putIfAbsent(channel, mutableSharedFlowOf(RoomState(channel)))
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
                else    -> message
            }

            onResult("PRIVMSG #$channel :$messageWithSuffix")
        }
    }

    private fun onWriterMessage(message: IrcMessage) {
        if (message.isLoginFailed()) {
            startedConnection = false
            makeAndPostSystemMessage(SystemMessageType.LOGIN_EXPIRED)
            return
        }

        when (message.command) {
            "PRIVMSG" -> Unit
            "366"     -> handleConnected(message.params[1].substring(1), writeConnection.isAnonymous)
            else      -> onMessage(message)
        }
    }

    private fun onReaderMessage(message: IrcMessage) {
        if (message.command == "PRIVMSG") {
            onMessage(message)
        }
    }

    private fun onMessage(msg: IrcMessage): List<ChatItem>? {
        when (msg.command) {
            "CLEARCHAT"       -> handleClearChat(msg)
            "ROOMSTATE"       -> handleRoomState(msg)
            "CLEARMSG"        -> handleClearMsg(msg)
            "USERSTATE",
            "GLOBALUSERSTATE" -> handleUserState(msg)
            else              -> handleMessage(msg)
        }
        return null
    }

    private fun handleDisconnect() {
        if (!hasDisconnected) {
            hasDisconnected = true
            val state = ConnectionState.DISCONNECTED
            connectionState.keys.forEach {
                connectionState[it]?.value = state
            }
            makeAndPostSystemMessage(state.toSystemMessageType())
        }
    }

    fun clearIgnores() {
        blockList.clear()
    }

    suspend fun setMentionEntries(stringSet: Set<String>) = withContext(Dispatchers.Default) {
        customMentionEntries = stringSet.mapToMention(adapter)
    }

    suspend fun setBlacklistEntries(stringSet: Set<String>) = withContext(Dispatchers.Default) {
        blacklistEntries = stringSet.mapToMention(adapter)
    }

    private fun handleConnected(channel: String, isAnonymous: Boolean) {
        hasDisconnected = false

        val state = when {
            isAnonymous -> ConnectionState.CONNECTED_NOT_LOGGED_IN
            else        -> ConnectionState.CONNECTED
        }
        makeAndPostSystemMessage(state.toSystemMessageType(), setOf(channel))
        connectionState[channel]?.value = state
    }

    private fun handleClearChat(msg: IrcMessage) {
        val parsed = TwitchMessage.parse(msg, emoteManager).map { ChatItem(it) }
        val target = if (msg.params.size > 1) msg.params[1] else ""
        val channel = msg.params[0].substring(1)

        messages[channel]?.value?.replaceWithTimeOuts(target)?.also {
            messages[channel]?.value = it.addAndLimit(parsed[0], scrollBackLength)
        }
    }

    private fun handleRoomState(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val state = roomStates[channel]?.firstValue ?: RoomState(channel)
        val updated = state.copyFromIrcMessage(msg)

        roomStates[channel]?.tryEmit(updated)
    }

    private fun handleUserState(msg: IrcMessage) {
        val id = msg.tags["user-id"]
        val sets = msg.tags["emote-sets"]?.split(",")
        val color = msg.tags["color"]
        val name = msg.tags["display-name"]

        val current = userState.value
        _userState.value = current.copy(
            userId = id ?: current.userId,
            color = color ?: current.color,
            displayName = name ?: current.displayName,
            emoteSets = sets ?: current.emoteSets
        )
    }

    private fun handleClearMsg(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val targetId = msg.tags["target-msg-id"] ?: return

        messages[channel]?.value?.replaceWithTimeOut(targetId)?.also {
            messages[channel]?.value = it
        }
    }

    private fun handleMessage(msg: IrcMessage) {
        msg.tags["user-id"]?.let { userId ->
            if (userId in blockList) return
        }
        val parsed = TwitchMessage.parse(msg, emoteManager).map {
            if (it.name == name) lastMessage[it.channel] = it.originalMessage
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
                    messages[it]?.value = currentChat.addAndLimit(parsed, scrollBackLength)
                }
            } else {
                val channel = msg.params[0].substring(1)
                val currentChat = messages[channel]?.value ?: emptyList()
                messages[channel]?.value = currentChat.addAndLimit(parsed, scrollBackLength)
                _notificationsFlow.tryEmit(parsed)

                if (msg.command == "WHISPER") {
                    (parsed[0].message as TwitchMessage).whisperRecipient = name
                    _whispers.value = _whispers.value.addAndLimit(parsed.toMentionTabItems(), scrollBackLength)
                    _channelMentionCount.increment("w", 1)
                }

                val mentions = parsed.filter { it.message is TwitchMessage && it.message.isMention && !it.message.isWhisper }.toMentionTabItems()
                if (mentions.isNotEmpty()) {
                    _channelMentionCount.increment(channel, mentions.size)
                    _mentions.value = _mentions.value.addAndLimit(mentions, scrollBackLength)
                }
            }
        }
    }

    private fun createUserCache(): LruCache<String, Boolean> {
        return object : LruCache<String, Boolean>(USER_CACHE_SIZE) {
            override fun equals(other: Any?): Boolean = false
        }
    }

    private fun makeAndPostSystemMessage(type: SystemMessageType, channels: Set<String> = messages.keys) {
        channels.forEach {
            val currentChat = messages[it]?.value ?: emptyList()
            messages[it]?.value = currentChat.addAndLimit(ChatItem(SystemMessage(type)), scrollBackLength)
        }
    }

    private fun ConnectionState.toSystemMessageType(): SystemMessageType = when (this) {
        ConnectionState.DISCONNECTED            -> SystemMessageType.DISCONNECTED
        ConnectionState.CONNECTED,
        ConnectionState.CONNECTED_NOT_LOGGED_IN -> SystemMessageType.CONNECTED
    }

    private suspend fun loadRecentMessages(channel: String) = withContext(Dispatchers.Default) {
        val result = apiManager.getRecentMessages(channel) ?: return@withContext
        val items = mutableListOf<ChatItem>()
        measureTimeMillis {
            for (message in result.messages) {
                val parsedIrc = IrcMessage.parse(message)
                if (parsedIrc.tags["user-id"] in blockList) continue

                for (msg in TwitchMessage.parse(parsedIrc, emoteManager)) {
                    if (!blacklistEntries.matches(msg.message, msg.name to msg.displayName, msg.emotes)) {
                        msg.checkForMention(name, customMentionEntries)
                        items += ChatItem(msg)
                    }
                }
            }
        }.let { Log.i(TAG, "Parsing message history for #$channel took $it ms") }

        val current = messages[channel]?.value ?: emptyList()
        messages[channel]?.value = items.addAndLimit(current, scrollBackLength, checkForDuplications = true)

        val mentions = items.filter { (it.message as TwitchMessage).isMention }.toMentionTabItems()
        _mentions.value = (_mentions.value + mentions).sortedBy { it.message.timestamp }
    }

    companion object {
        private val TAG = ChatRepository::class.java.simpleName
        private val INVISIBLE_CHAR = ByteBuffer.allocate(4).putInt(0x000E0000).array().toString(Charsets.UTF_32)
        private const val USER_CACHE_SIZE = 500
        const val MENTIONS_KEY = "mentions"
    }
}