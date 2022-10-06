package com.flxrs.dankchat.data.repo

import android.graphics.Color
import android.util.Log
import androidx.collection.LruCache
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.toMentionTabItems
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.api.bodyOrNull
import com.flxrs.dankchat.data.api.dto.RecentMessagesDto
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.twitch.connection.*
import com.flxrs.dankchat.data.twitch.message.*
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.collections.set
import kotlin.system.measureTimeMillis

class ChatRepository @Inject constructor(
    private val apiManager: ApiManager,
    private val emoteRepository: EmoteRepository,
    private val readConnection: ChatConnection,
    private val writeConnection: ChatConnection,
    private val pubSubManager: PubSubManager,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val highlightsRepository: HighlightsRepository,
    private val ignoresRepository: IgnoresRepository,
    scope: CoroutineScope,
) {

    data class UserState(
        val userId: String = "",
        val color: String? = null,
        val displayName: String = "",
        val globalEmoteSets: List<String> = listOf(),
        val followerEmoteSets: Map<String, List<String>> = emptyMap(),
        val moderationChannels: Set<String> = emptySet(),
        val vipChannels: Set<String> = emptySet(),
    ) {

        fun getSendDelay(channel: String): Long = when {
            hasHighRateLimit(channel) -> LOW_SEND_DELAY_MS
            else                      -> REGULAR_SEND_DELAY_MS
        }

        private fun hasHighRateLimit(channel: String): Boolean = channel in moderationChannels || channel in vipChannels

        companion object {
            private const val REGULAR_SEND_DELAY_MS = 1200L
            private const val LOW_SEND_DELAY_MS = 150L
        }
    }

    private val _activeChannel = MutableStateFlow("")
    private val _channels = MutableStateFlow<List<String>?>(null)

    private val _notificationsFlow = MutableSharedFlow<List<ChatItem>>(0, extraBufferCapacity = 10)
    private val _channelMentionCount = mutableSharedFlowOf(mutableMapOf<String, Int>())
    private val _unreadMessagesMap = mutableSharedFlowOf(mutableMapOf<String, Boolean>())
    private val messages = ConcurrentHashMap<String, MutableStateFlow<List<ChatItem>>>()
    private val _mentions = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _whispers = MutableStateFlow<List<ChatItem>>(emptyList())
    private val connectionState = ConcurrentHashMap<String, MutableStateFlow<ConnectionState>>()
    private val roomStates = ConcurrentHashMap<String, MutableSharedFlow<RoomState>>()
    private val users = ConcurrentHashMap<String, LruCache<String, Boolean>>()
    private val usersFlows = ConcurrentHashMap<String, MutableStateFlow<Set<String>>>()
    private val userState = MutableStateFlow(UserState())
    private val blockList = mutableSetOf<String>() // TODO extract out of repo to common data source

    private var lastMessage = ConcurrentHashMap<String, String>()
    private val loadedRecentsInChannels = mutableSetOf<String>()
    private val knownRewards = ConcurrentHashMap<String, PubSubMessage.PointRedemption>()

    init {
        scope.launch {
            readConnection.messages.collect { event ->
                when (event) {
                    is ChatEvent.Connected          -> handleConnected(event.channel, event.isAnonymous)
                    is ChatEvent.Closed             -> handleDisconnect()
                    is ChatEvent.ChannelNonExistent -> makeAndPostSystemMessage(SystemMessageType.ChannelNonExistent(event.channel), setOf(event.channel))
                    is ChatEvent.LoginFailed        -> makeAndPostSystemMessage(SystemMessageType.LoginExpired)
                    is ChatEvent.Message            -> onMessage(event.message)
                    is ChatEvent.Error              -> handleDisconnect()
                }
            }
        }
        scope.launch {
            writeConnection.messages.collect { event ->
                if (event !is ChatEvent.Message) return@collect
                onWriterMessage(event.message)
            }
        }
        scope.launch {
            pubSubManager.messages.collect { pubSubMessage ->
                when (pubSubMessage) {
                    is PubSubMessage.PointRedemption -> {
                        if (pubSubMessage.data.user.id in blockList) return@collect
                        knownRewards[pubSubMessage.data.id] = pubSubMessage

                        if (!pubSubMessage.data.reward.requiresUserInput) {
                            val parsed = PointRedemptionMessage.parsePointReward(pubSubMessage.timestamp, pubSubMessage.data)
                            messages[pubSubMessage.channelName]?.update {
                                it.addAndLimit(ChatItem(parsed), scrollBackLength)
                            }
                        }
                    }

                    is PubSubMessage.Whisper         -> {
                        if (pubSubMessage.data.userId in blockList) return@collect

                        val parsed = runCatching {
                            WhisperMessage.fromPubSub(pubSubMessage.data)
                        }.getOrElse { return@collect }

                        val messageWithIgnores = ignoresRepository.applyIgnores(parsed) ?: return@collect
                        val messageWithHighlights = highlightsRepository.calculateHighlightState(messageWithIgnores)
                        val message = emoteRepository.parseEmotesAndBadges(messageWithHighlights) as WhisperMessage

                        val item = ChatItem(message, isMentionTab = true)
                        _whispers.update { current ->
                            current.addAndLimit(item, scrollBackLength)
                        }

                        if (pubSubMessage.data.userId == userState.value.userId) {
                            return@collect
                        }

                        val currentUsers = users["*"] ?: createUserCache()
                        currentUsers.put(message.name, true)
                        usersFlows["*"]?.update {
                            currentUsers.snapshot().keys
                        }
                        _channelMentionCount.increment("w", 1)
                        _notificationsFlow.tryEmit(listOf(item))
                    }
                }

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
    fun getUsers(channel: String): StateFlow<Set<String>> = usersFlows.getOrPut(channel) { MutableStateFlow(emptySet()) }

    suspend fun getLatestValidUserState(minChannelsSize: Int = 0): UserState = userState
        .filter {
            it.userId.isNotBlank() && it.followerEmoteSets.size >= minChannelsSize
        }.take(count = 1).single()

    suspend fun loadRecentMessages(channel: String, loadHistory: Boolean, isUserChange: Boolean) {
        when {
            isUserChange -> return
            loadHistory  -> loadRecentMessages(channel)
            else         -> messages[channel]?.update { current ->
                val message = SystemMessageType.NoHistoryLoaded.toChatItem()
                listOf(message).addAndLimit(current, scrollBackLength, checkForDuplications = true)
            }
        }
    }

    suspend fun loadUserBlocks(id: String) = withContext(Dispatchers.Default) {
        if (!dankChatPreferenceStore.isLoggedIn) {
            return@withContext
        }

        apiManager.getUserBlocks(id)?.let { (data) ->
            blockList.clear()
            blockList.addAll(data.map { it.id })
        }
    }

    fun isUserBlocked(targetUserId: String): Boolean = targetUserId in blockList
    fun addUserBlock(targetUserId: String) = blockList.add(targetUserId)
    fun removeUserBlock(targetUserId: String) = blockList.remove(targetUserId)

    suspend fun loadChatters(channel: String) = withContext(Dispatchers.Default) {
        measureTimeMillis {
            apiManager.getChatters(channel)?.let { chatters ->
                val currentUsers = users
                    .getOrPut(channel) { createUserCache() }
                    .also { cache ->
                        chatters.total.forEach { cache.put(it, true) }
                    }

                usersFlows
                    .getOrPut(channel) { MutableStateFlow(emptySet()) }
                    .update { currentUsers.snapshot().keys }
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

    fun closeAndReconnect() {
        val channels = channels.value.orEmpty()

        readConnection.close()
        writeConnection.close()
        pubSubManager.close()
        connectAndJoin(channels)
    }

    fun reconnect() {
        readConnection.reconnect()
        writeConnection.reconnect()
        pubSubManager.reconnect()
    }

    fun reconnectIfNecessary() {
        readConnection.reconnectIfNecessary()
        writeConnection.reconnectIfNecessary()
        pubSubManager.reconnectIfNecessary()
    }

    fun getLastMessage(): String? = lastMessage[activeChannel.value]?.trimEndSpecialChar()

    fun sendMessage(input: String) {
        val channel = activeChannel.value
        val preparedMessage = prepareMessage(channel, input) ?: return
        writeConnection.sendMessage(preparedMessage)

        if (pubSubManager.connectedAndHasWhisperTopic) {
            return
        }
        // fake whisper handling
        val split = input.split(" ")
        if (split.size > 2 && (split[0] == "/w" || split[0] == ".w") && split[1].isNotBlank()) {
            val message = input.substring(4 + split[1].length)
            val emotes = emoteRepository.parse3rdPartyEmotes(message, withTwitch = true)
            val userState = userState.value
            val fakeMessage = WhisperMessage(
                userId = userState.userId,
                name = dankChatPreferenceStore.userName.orEmpty(),
                displayName = userState.displayName,
                color = Color.parseColor(userState.color ?: Message.DEFAULT_COLOR),
                recipientId = null,
                recipientColor = Color.parseColor(Message.DEFAULT_COLOR),
                recipientName = split[1],
                recipientDisplayName = split[1],
                message = message,
                rawEmotes = "",
                rawBadges = "",
                emotes = emotes,
            )
            val fakeItem = ChatItem(fakeMessage, isMentionTab = true)
            _whispers.update {
                it.addAndLimit(fakeItem, scrollBackLength)
            }
        }
    }

    fun connectAndJoin(channels: List<String> = dankChatPreferenceStore.getChannels()) {
        if (!pubSubManager.connected) {
            pubSubManager.start()
        }

        if (!readConnection.connected) {
            val oAuth = dankChatPreferenceStore.oAuthKey.orEmpty()
            val name = dankChatPreferenceStore.userName.orEmpty()
            connect(name, oAuth)
            joinChannels(channels)
        }
    }

    fun joinChannel(channel: String, listenToPubSub: Boolean = true): List<String> {
        val channels = channels.value.orEmpty()
        if (channel in channels)
            return channels

        val updatedChannels = channels + channel
        _channels.value = updatedChannels

        createFlowsIfNecessary(channel)
        messages[channel]?.value = emptyList()


        readConnection.joinChannel(channel)

        if (listenToPubSub) {
            pubSubManager.addChannel(channel)
        }

        return updatedChannels
    }

    fun partChannel(channel: String, unListenFromPubSub: Boolean = true): List<String> {
        val updatedChannels = channels.value.orEmpty() - channel
        _channels.value = updatedChannels

        removeChannelData(channel)
        readConnection.partChannel(channel)
        writeConnection.partChannel(channel)

        if (unListenFromPubSub) {
            pubSubManager.removeChannel(channel)
        }

        return updatedChannels
    }

    fun updateChannels(updatedChannels: List<String>) {
        val currentChannels = channels.value.orEmpty()
        val removedChannels = currentChannels - updatedChannels.toSet()

        removedChannels.forEach {
            partChannel(it)
        }

        _channels.value = updatedChannels
    }

    fun clearIgnores() {
        blockList.clear()
    }

    // TODO should be null if anon
    private fun connect(userName: String, oauth: String) {
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
        messages.remove(channel)
        messages.remove(channel)
        usersFlows.remove(channel)
        users.remove(channel)
        lastMessage.remove(channel)
        _channelMentionCount.clear(channel)
        loadedRecentsInChannels.remove(channel)
    }

    private fun createFlowsIfNecessary(channel: String) {
        messages.putIfAbsent(channel, MutableStateFlow(emptyList()))
        connectionState.putIfAbsent(channel, MutableStateFlow(ConnectionState.DISCONNECTED))
        roomStates.putIfAbsent(channel, mutableSharedFlowOf(RoomState(channel)))
        users.putIfAbsent(channel, createUserCache())
        usersFlows.putIfAbsent(channel, MutableStateFlow(emptySet()))

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

        val messageWithEmojiFix = messageWithSuffix.replace(ZERO_WIDTH_JOINER, ESCAPE_TAG)
        lastMessage[channel] = messageWithEmojiFix
        return "PRIVMSG #$channel :$messageWithEmojiFix"
    }

    private suspend fun onMessage(msg: IrcMessage): List<ChatItem>? {
        when (msg.command) {
            "CLEARCHAT"       -> handleClearChat(msg)
            "ROOMSTATE"       -> handleRoomState(msg)
            "CLEARMSG"        -> handleClearMsg(msg)
            "USERSTATE"       -> handleUserState(msg)
            "GLOBALUSERSTATE" -> handleGlobalUserState(msg)
            "WHISPER"         -> handleWhisper(msg)
            else              -> handleMessage(msg)
        }
        return null
    }

    private suspend fun onWriterMessage(message: IrcMessage) {
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

    private fun handleConnected(channel: String, isAnonymous: Boolean) {
        val state = when {
            isAnonymous -> ConnectionState.CONNECTED_NOT_LOGGED_IN
            else        -> ConnectionState.CONNECTED
        }
        makeAndPostSystemMessage(state.toSystemMessageType(), setOf(channel))
        connectionState[channel]?.value = state
    }

    private fun handleClearChat(msg: IrcMessage) {
        val parsed = runCatching {
            ClearChatMessage.parseClearChat(msg)
        }.getOrElse {
            return
        }

        messages[parsed.channel]?.update { current ->
            current.replaceWithTimeOuts(parsed, scrollBackLength)
        }
    }

    private fun handleRoomState(msg: IrcMessage) {
        val channel = msg.params.getOrNull(0)?.substring(1) ?: return
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
        val channel = msg.params.getOrNull(0)?.substring(1) ?: return
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
        val channel = msg.params.getOrNull(0)?.substring(1) ?: return
        val targetId = msg.tags["target-msg-id"] ?: return

        messages[channel]?.update { current ->
            current.replaceWithTimeOut(targetId)
        }
    }

    private fun handleWhisper(ircMessage: IrcMessage) {
        if (pubSubManager.connectedAndHasWhisperTopic) {
            return
        }

        // TODO
        val userId = ircMessage.tags["user-id"]
        if (userId in blockList) {
            return
        }

        val userState = userState.value
        val parsed = runCatching {
            WhisperMessage.parseFromIrc(ircMessage, userState.displayName, userState.color)
        }.getOrElse { return }

        val messageWithIgnores = ignoresRepository.applyIgnores(parsed) ?: return
        val messageWithHighlights = highlightsRepository.calculateHighlightState(messageWithIgnores)
        val message = emoteRepository.parseEmotesAndBadges(messageWithHighlights) as WhisperMessage

        val currentUsers = users
            .getOrPut("*") { createUserCache() }
            .also { it.put(message.name, true) }

        usersFlows
            .getOrPut("*") { MutableStateFlow(emptySet()) }
            .update { currentUsers.snapshot().keys }

        val item = ChatItem(message, isMentionTab = true)
        _whispers.update { current ->
            current.addAndLimit(item, scrollBackLength)
        }
        _channelMentionCount.increment("w", 1)
    }

    private suspend fun handleMessage(ircMessage: IrcMessage) {
        // TODO
        val userId = ircMessage.tags["user-id"]
        if (userId in blockList) {
            return
        }

        val rewardId = ircMessage.tags["custom-reward-id"]
        val additionalMessages = when {
            rewardId != null -> {
                val reward = knownRewards[rewardId]
                    ?.also { knownRewards.remove(rewardId) }
                    ?: run {
                        withTimeoutOrNull(PUBSUB_TIMEOUT) {
                            pubSubManager.messages.first {
                                it is PubSubMessage.PointRedemption && it.data.reward.id == rewardId
                            } as PubSubMessage.PointRedemption
                        }
                    }

                reward?.let {
                    listOf(ChatItem(PointRedemptionMessage.parsePointReward(it.timestamp, it.data)))
                }.orEmpty()
            }

            else             -> emptyList()
        }

        val rawMessage = runCatching { Message.parse(ircMessage) }
            .getOrElse {
                Log.e(TAG, "Failed to parse message", it)
                return
            } ?: return

        val messageWithIgnores = ignoresRepository.applyIgnores(rawMessage) ?: return
        val messageWithHighlights = highlightsRepository.calculateHighlightState(messageWithIgnores)
        val message = emoteRepository.parseEmotesAndBadges(messageWithHighlights)

        if (message is NoticeMessage && message.channel == "*") {
            messages.keys.forEach {
                messages[it]?.update { current ->
                    current.addAndLimit(ChatItem(message), scrollBackLength)
                }
            }
            return
        }

        if (message is PrivMessage) {
            if (message.name == dankChatPreferenceStore.userName) {
                val previousLastMessage = lastMessage[message.channel]?.trimEndSpecialChar()
                if (previousLastMessage != message.originalMessage.trimEndSpecialChar()) {
                    lastMessage[message.channel] = message.originalMessage
                }

                val hasVip = message.badges.any { badge -> badge.badgeTag?.startsWith("vip") == true }
                userState.update { userState ->
                    val updatedVipChannels = when {
                        hasVip -> userState.vipChannels + message.channel
                        else   -> userState.vipChannels - message.channel

                    }
                    userState.copy(vipChannels = updatedVipChannels)
                }
            }

            val currentUsers = users
                .getOrPut(message.channel) { createUserCache() }
                .also { cache -> cache.put(message.name, true) }
            usersFlows
                .getOrPut(message.channel) { MutableStateFlow(emptySet()) }
                .update { currentUsers.snapshot().keys }
        }

        val items = buildList {
            add(ChatItem(message))
            if (message is UserNoticeMessage && message.childMessage != null) {
                add(ChatItem(message.childMessage))
            }
        }

        val channel = when (rawMessage) {
            is PrivMessage       -> rawMessage.channel
            is UserNoticeMessage -> rawMessage.channel
            is NoticeMessage     -> rawMessage.channel
            else                 -> return
        }

        messages[channel]?.update { current ->
            current.addAndLimit(additionalMessages + items, scrollBackLength)
        }

        _notificationsFlow.tryEmit(items)
        when (ircMessage.command) {
            "PRIVMSG", "USERNOTICE" -> {
                val isUnread = _unreadMessagesMap.firstValue[channel]
                if (channel != activeChannel.value && (isUnread == null || isUnread == false)) {
                    _unreadMessagesMap.assign(channel, true)
                }
            }
        }

        val mentions = items
            .filter { it.message.highlights.hasMention() }
            .toMentionTabItems()

        if (mentions.isNotEmpty()) {
            _channelMentionCount.increment(channel, mentions.size)
            _mentions.update { current ->
                current.addAndLimit(mentions, scrollBackLength)
            }
        }
    }

    private fun createUserCache(): LruCache<String, Boolean> = LruCache(USER_CACHE_SIZE)

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
        if (channel in loadedRecentsInChannels) {
            return@withContext
        }

        val response = runCatching {
            apiManager.getRecentMessages(channel)
        }.getOrElse {
            makeAndPostSystemMessage(SystemMessageType.MessageHistoryUnavailable(status = null), setOf(channel))
            return@withContext
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyOrNull<RecentMessagesDto>()
            val type = when (body?.errorCode) {
                RecentMessagesDto.ERROR_CHANNEL_IGNORED -> {
                    loadedRecentsInChannels += channel // not a temporary error, so we don't want to retry
                    SystemMessageType.MessageHistoryIgnored
                }

                else                                    -> SystemMessageType.MessageHistoryUnavailable(status = response.status.value.toString())
            }
            makeAndPostSystemMessage(type, setOf(channel))
            return@withContext
        }

        loadedRecentsInChannels += channel
        val result = response.bodyOrNull<RecentMessagesDto>()
        val recentMessages = result?.messages.orEmpty()
        val items = mutableListOf<ChatItem>()
        measureTimeMillis {
            for (recentMessage in recentMessages) {
                val parsedIrc = IrcMessage.parse(recentMessage)
                if (parsedIrc.tags["user-id"] in blockList) {
                    continue
                }

                val rawMessage = runCatching {
                    Message.parse(parsedIrc)
                }.getOrNull() ?: continue

                val messageWithIgnores = ignoresRepository.applyIgnores(rawMessage) ?: continue
                val messageWithHighlights = highlightsRepository.calculateHighlightState(messageWithIgnores)
                val message = emoteRepository.parseEmotesAndBadges(messageWithHighlights)


                items += ChatItem(message)
                if (message is UserNoticeMessage && message.childMessage != null) {
                    items += ChatItem(message.childMessage)
                }
            }
        }.let { Log.i(TAG, "Parsing message history for #$channel took $it ms") }

        messages[channel]?.update { current ->
            val withIncompleteWarning = when {
                recentMessages.isNotEmpty() && result?.errorCode == RecentMessagesDto.ERROR_CHANNEL_NOT_JOINED -> {
                    current + ChatItem(SystemMessage(SystemMessageType.MessageHistoryIncomplete))
                }

                else                                                                                           -> current
            }
            items.addAndLimit(withIncompleteWarning, scrollBackLength, checkForDuplications = true)
        }

        val mentions = items.filter { (it.message.highlights.hasMention()) }.toMentionTabItems()
        _mentions.update { current ->
            (current + mentions).sortedBy { it.message.timestamp }
        }
    }

    companion object {
        private val TAG = ChatRepository::class.java.simpleName
        private val INVISIBLE_CHAR = 0x000E0000.codePointAsString
        private val ESCAPE_TAG = 0x000E0002.codePointAsString
        private const val USER_CACHE_SIZE = 5000
        private const val PUBSUB_TIMEOUT = 1000L

        val ESCAPE_TAG_REGEX = "(?<!$ESCAPE_TAG)$ESCAPE_TAG".toRegex()
        const val ZERO_WIDTH_JOINER = 0x200D.toChar().toString()
    }
}