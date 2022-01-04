package com.flxrs.dankchat.service

import android.util.Log
import androidx.collection.LruCache
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.toMentionTabItems
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.flxrs.dankchat.service.api.ApiManager
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.ChatEvent
import com.flxrs.dankchat.service.twitch.connection.ConnectionState
import com.flxrs.dankchat.service.twitch.connection.WebSocketChatConnection
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import com.flxrs.dankchat.service.twitch.message.*
import com.flxrs.dankchat.utils.extensions.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlin.collections.set
import kotlin.system.measureTimeMillis

class ChatRepository @Inject constructor(
    private val apiManager: ApiManager,
    private val emoteManager: EmoteManager,
    private val readConnection: WebSocketChatConnection,
    private val writeConnection: WebSocketChatConnection,
    scope: CoroutineScope,
) {

    data class UserState(
        val userId: String = "",
        val color: String? = null,
        val displayName: String = "",
        val globalEmoteSets: List<String> = listOf(),
        val followerEmoteSets: Map<String, List<String>> = emptyMap(),
        val moderationChannels: List<String> = emptyList(),
    )

    private val _activeChannel = MutableStateFlow("")
    private val _channels = MutableStateFlow<List<String>?>(null)

    private val _notificationsFlow = MutableSharedFlow<List<ChatItem>>(0, extraBufferCapacity = 10)
    private val _channelMentionCount = mutableSharedFlowOf(mutableMapOf<String, Int>())
    private val _unreadMessagesMap = mutableSharedFlowOf(mutableMapOf<String, Boolean>())
    private val messages = mutableMapOf<String, MutableStateFlow<List<ChatItem>>>()
    private val _mentions = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _whispers = MutableStateFlow<List<ChatItem>>(emptyList())
    private val connectionState = mutableMapOf<String, MutableStateFlow<ConnectionState>>()
    private val roomStates = mutableMapOf<String, MutableSharedFlow<RoomState>>()
    private val users = mutableMapOf<String, MutableStateFlow<LruCache<String, Boolean>>>()
    private val userState = MutableStateFlow(UserState())
    private val blockList = mutableSetOf<String>() // TODO extract out of repo to common data source

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(MultiEntryItem.Entry::class.java)

    private var customMentionEntries = listOf<Mention>()
    private var blacklistEntries = listOf<Mention>()
    private var name: String = ""
    private var lastMessage = mutableMapOf<String, String>()
    private val loadedRecentsInChannels = mutableSetOf<String>()

    init {
        scope.launch {
            readConnection.messages.collect { event ->
                when (event) {
                    is ChatEvent.Connected   -> handleConnected(event.channel, event.isAnonymous)
                    is ChatEvent.Closed      -> handleDisconnect()
                    is ChatEvent.LoginFailed -> makeAndPostSystemMessage(SystemMessageType.LoginExpired)
                    is ChatEvent.Message     -> onMessage(event.message)
                    is ChatEvent.Error       -> handleDisconnect()
                }
            }
        }
        scope.launch {
            writeConnection.messages.collect { event ->
                if (event !is ChatEvent.Message) return@collect
                onWriterMessage(event.message)
            }
        }
    }

    val notificationsFlow: SharedFlow<List<ChatItem>> = _notificationsFlow.asSharedFlow()
    val channelMentionCount: SharedFlow<Map<String, Int>> = _channelMentionCount.asSharedFlow()
    val unreadMessagesMap: SharedFlow<Map<String, Boolean>> = _unreadMessagesMap.asSharedFlow()
    val userStateFlow: StateFlow<UserState> = userState.asStateFlow()
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

    suspend fun getLatestValidUserState(minChannelsSize: Int = 0): UserState = userState
        .filter {
            it.userId.isNotBlank() && it.followerEmoteSets.size >= minChannelsSize
        }.take(count = 1).single()

    suspend fun loadRecentMessages(channel: String, loadHistory: Boolean, isUserChange: Boolean) {
        when {
            isUserChange -> return
            loadHistory  -> loadRecentMessages(channel)
            else         -> messages[channel]?.update { current ->
                listOf(ChatItem(SystemMessage(type = SystemMessageType.NoHistoryLoaded), false)) + current
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

    fun clearUnreadMessage(channel: String) {
        _unreadMessagesMap.assign(channel, false)
    }

    fun clear(channel: String) {
        messages[channel]?.value = emptyList()
    }

    fun closeAndReconnect(name: String, oAuth: String) {
        val channels = channels.value.orEmpty()

        readConnection.close()
        writeConnection.close()
        connectAndJoin(name, oAuth, channels)
    }

    fun reconnect() {
        readConnection.reconnect()
        writeConnection.reconnect()
    }

    fun reconnectIfNecessary() {
        readConnection.reconnectIfNecessary()
        writeConnection.reconnectIfNecessary()
    }

    fun getLastMessage(): String? = lastMessage[activeChannel.value]?.trimEndSpecialChar()

    fun sendMessage(input: String) {
        val channel = activeChannel.value
        val preparedMessage = prepareMessage(channel, input) ?: return
        writeConnection.sendMessage(preparedMessage)

        val split = input.split(" ")
        if (split.size > 2 && split[0] == "/w" && split[1].isNotBlank()) {
            val message = input.substring(4 + split[1].length)
            val emotes = emoteManager.parse3rdPartyEmotes(message, withTwitch = true)
            val fakeMessage = TwitchMessage(channel = "", name = name, displayName = name, message = message, emotes = emotes, isWhisper = true, whisperRecipient = split[1])
            val fakeItem = ChatItem(fakeMessage, isMentionTab = true)
            _whispers.value = _whispers.value.addAndLimit(fakeItem, scrollBackLength)
        }
    }

    fun connectAndJoin(name: String, oAuth: String, channels: List<String>) {
        if (readConnection.connected) return

        connect(name, oAuth)
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

    // TODO should be null if anon
    private fun connect(userName: String, oauth: String) {
        name = userName
        readConnection.connect(userName, oauth)
        writeConnection.connect(userName, oauth)
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
    }

    private fun removeChannelData(channel: String) {
        messages[channel]?.value = emptyList()
        lastMessage.remove(channel)
        _channelMentionCount.clear(channel)
        loadedRecentsInChannels.remove(channel)
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

    private fun prepareMessage(channel: String, message: String): String? {
        if (message.isBlank()) return null
        val trimmedMessage = message.trimEnd()

        val messageWithSuffix = when (lastMessage[channel] ?: "") {
            trimmedMessage -> "$trimmedMessage $INVISIBLE_CHAR"
            else           -> trimmedMessage
        }
        return "PRIVMSG #$channel :$messageWithSuffix"
    }

    private fun onMessage(msg: IrcMessage): List<ChatItem>? {
        when (msg.command) {
            "CLEARCHAT"       -> handleClearChat(msg)
            "ROOMSTATE"       -> handleRoomState(msg)
            "CLEARMSG"        -> handleClearMsg(msg)
            "USERSTATE"       -> handleUserState(msg)
            "GLOBALUSERSTATE" -> handleGlobalUserState(msg)
            else              -> handleMessage(msg)
        }
        return null
    }

    private fun onWriterMessage(message: IrcMessage) {
        when (message.command) {
            "USERSTATE"       -> handleUserState(message)
            "GLOBALUSERSTATE" -> handleGlobalUserState(message)
            "NOTICE"          -> handleMessage(message)
        }
    }

    private fun handleDisconnect() {
        val state = ConnectionState.DISCONNECTED
        connectionState.keys.forEach {
            connectionState[it]?.value = state
        }
        makeAndPostSystemMessage(state.toSystemMessageType())

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
        val state = when {
            isAnonymous -> ConnectionState.CONNECTED_NOT_LOGGED_IN
            else        -> ConnectionState.CONNECTED
        }
        makeAndPostSystemMessage(state.toSystemMessageType(), setOf(channel))
        connectionState[channel]?.value = state
    }

    private fun handleClearChat(msg: IrcMessage) {
        val parsed = ClearChatMessage.parseClearChat(msg)

        messages[parsed.channel]?.update { current ->
            current.replaceWithTimeOuts(parsed, scrollBackLength)
        }
    }

    private fun handleRoomState(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val state = roomStates[channel]?.firstValue ?: RoomState(channel)
        val updated = state.copyFromIrcMessage(msg)

        roomStates[channel]?.tryEmit(updated)
    }

    private fun handleGlobalUserState(msg: IrcMessage) {
        val id = msg.tags["user-id"]
        val sets = msg.tags["emote-sets"]?.split(",")
        val color = msg.tags["color"]
        val name = msg.tags["display-name"]
        userState.update { current ->
            current.copy(
                userId = id ?: current.userId,
                color = color ?: current.color,
                displayName = name ?: current.displayName,
                globalEmoteSets = sets ?: current.globalEmoteSets
            )
        }
    }

    private fun handleUserState(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val id = msg.tags["user-id"]
        val sets = msg.tags["emote-sets"]?.split(",").orEmpty()
        val color = msg.tags["color"]
        val name = msg.tags["display-name"]
        val badges = msg.tags["badges"]?.split(",")
        val hasModeration = badges?.any { it.contains("broadcaster") || it.contains("moderator") } ?: false
        userState.update { current ->
            val followerEmotes = when {
                current.globalEmoteSets.isNotEmpty() -> sets - current.globalEmoteSets.toSet()
                else                                 -> emptyList()
            }
            val newFollowerEmoteSets = current.followerEmoteSets.toMutableMap()
            newFollowerEmoteSets[channel] = followerEmotes

            val newModerationChannels = when {
                hasModeration -> current.moderationChannels + channel
                else          -> current.moderationChannels
            }

            current.copy(
                userId = id ?: current.userId,
                color = color ?: current.color,
                displayName = name ?: current.displayName,
                followerEmoteSets = newFollowerEmoteSets,
                moderationChannels = newModerationChannels
            )
        }
    }

    private fun handleClearMsg(msg: IrcMessage) {
        val channel = msg.params[0].substring(1)
        val targetId = msg.tags["target-msg-id"] ?: return

        messages[channel]?.update { current ->
            current.replaceWithTimeOut(targetId)
        }
    }

    private fun handleMessage(ircMessage: IrcMessage) {
        val userId = ircMessage.tags["user-id"]
        if (userId in blockList) {
            return
        }

        val items = Message.parse(ircMessage, emoteManager, name).map {
            it as TwitchMessage // TODO
            if (it.name == name) {
                lastMessage[it.channel] = it.originalMessage
            }

            if (blacklistEntries.matches(it.message, it.name to it.displayName, it.emotes)) {
                return
            }

            val withMentions = it.checkForMention(name, customMentionEntries)
            val currentUsers = users[withMentions.channel]?.value ?: createUserCache()
            currentUsers.put(withMentions.name, true)
            users[withMentions.channel]?.value = currentUsers

            ChatItem(withMentions)
        }

        if (items.isEmpty()) {
            return
        }

        val mainMessage = items.first().message as TwitchMessage // TODO ugh
        val channel = mainMessage.channel
        if (channel == "*" && !mainMessage.isWhisper) {
            messages.keys.forEach {
                messages[it]?.update { current ->
                    current.addAndLimit(items, scrollBackLength)
                }
            }
            return
        } else if (!mainMessage.isWhisper) {
            messages[channel]?.update { current ->
                current.addAndLimit(items, scrollBackLength)
            }
        }

        _notificationsFlow.tryEmit(items)
        when (ircMessage.command) {
            "WHISPER"               -> {
                _whispers.update { current ->
                    current.addAndLimit(items.toMentionTabItems(), scrollBackLength)
                }
                _channelMentionCount.increment("w", 1)
            }
            "PRIVMSG", "USERNOTICE" -> {
                val isUnread = _unreadMessagesMap.firstValue[channel]
                if (isUnread == null || isUnread == false) {
                    _unreadMessagesMap.assign(channel, true)
                }
            }
        }

        val mentions = items.filter {
            it.message is TwitchMessage && it.message.isMention && !it.message.isWhisper
        }.toMentionTabItems()

        if (mentions.isNotEmpty()) {
            _channelMentionCount.increment(channel, mentions.size)
            _mentions.update { current ->
                current.addAndLimit(mentions, scrollBackLength)
            }
        }
    }

    private fun createUserCache(): LruCache<String, Boolean> {
        return object : LruCache<String, Boolean>(USER_CACHE_SIZE) {
            override fun equals(other: Any?): Boolean = false
        }
    }

    fun makeAndPostCustomSystemMessage(message: String, channel: String) {
        messages[channel]?.update {
            it.addAndLimit(ChatItem(SystemMessage(SystemMessageType.Custom(message))), scrollBackLength)
        }
    }

    private fun makeAndPostSystemMessage(type: SystemMessageType, channels: Set<String> = messages.keys) {
        channels.forEach { channel ->
            messages[channel]?.update { current ->
                current.addAndLimit(ChatItem(SystemMessage(type)), scrollBackLength)
            }
        }
    }

    private fun ConnectionState.toSystemMessageType(): SystemMessageType = when (this) {
        ConnectionState.DISCONNECTED            -> SystemMessageType.Disconnected
        ConnectionState.CONNECTED,
        ConnectionState.CONNECTED_NOT_LOGGED_IN -> SystemMessageType.Connected
    }

    private suspend fun loadRecentMessages(channel: String) = withContext(Dispatchers.Default) {
        if (channel in loadedRecentsInChannels) return@withContext
        val result = apiManager.getRecentMessages(channel) ?: return@withContext
        val recentMessages = result.messages ?: return@withContext

        loadedRecentsInChannels += channel
        val items = mutableListOf<ChatItem>()
        measureTimeMillis {
            for (message in recentMessages) {
                val parsedIrc = IrcMessage.parse(message)
                if (parsedIrc.tags["user-id"] in blockList) continue

                loop@ for (msg in Message.parse(parsedIrc, emoteManager, name)) {
                    val withMention = when (msg) {
                        is TwitchMessage -> {
                            if (blacklistEntries.matches(msg.message, msg.name to msg.displayName, msg.emotes)) {
                                continue@loop
                            }
                            msg.checkForMention(name, customMentionEntries)
                        }
                        else             -> msg
                    }

                    items += ChatItem(withMention)
                }
            }
        }.let { Log.i(TAG, "Parsing message history for #$channel took $it ms") }

        messages[channel]?.update { current ->
            items.addAndLimit(current, scrollBackLength, checkForDuplications = true)
        }

        val mentions = items.filter { (it.message as? TwitchMessage)?.isMention == true }.toMentionTabItems()
        _mentions.update { current ->
            (current + mentions).sortedBy { it.message.timestamp }
        }
    }

    companion object {
        private val TAG = ChatRepository::class.java.simpleName
        private val INVISIBLE_CHAR = ByteBuffer.allocate(4).putInt(0x000E0000).array().toString(Charsets.UTF_32)
        private const val USER_CACHE_SIZE = 500
        const val MENTIONS_KEY = "mentions"
    }
}