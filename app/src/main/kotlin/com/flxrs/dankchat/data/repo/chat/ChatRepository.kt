package com.flxrs.dankchat.data.repo.chat

import android.graphics.Color
import android.util.Log
import androidx.collection.LruCache
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.toMentionTabItems
import com.flxrs.dankchat.data.api.chatters.ChattersApiClient
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApiClient
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApiException
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesError
import com.flxrs.dankchat.data.api.recentmessages.dto.RecentMessagesDto
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.repo.EmoteRepository
import com.flxrs.dankchat.data.repo.HighlightsRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.twitch.connection.*
import com.flxrs.dankchat.data.twitch.message.*
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.di.ReadConnection
import com.flxrs.dankchat.di.WriteConnection
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.set
import kotlin.system.measureTimeMillis

@Singleton
class ChatRepository @Inject constructor(
    private val recentMessagesApiClient: RecentMessagesApiClient,
    private val chattersApiClient: ChattersApiClient,
    private val emoteRepository: EmoteRepository,
    private val highlightsRepository: HighlightsRepository,
    private val ignoresRepository: IgnoresRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val pubSubManager: PubSubManager,
    @ReadConnection private val readConnection: ChatConnection,
    @WriteConnection private val writeConnection: ChatConnection,
    @ApplicationScope scope: CoroutineScope,
) {

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
    private val users = ConcurrentHashMap<String, LruCache<String, String>>()
    private val usersFlows = ConcurrentHashMap<String, MutableStateFlow<Set<String>>>()
    private val userState = MutableStateFlow(UserState())
    private val _chatLoadingFailures = MutableStateFlow(emptySet<ChatLoadingFailure>())

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
                        if (ignoresRepository.isUserBlocked(pubSubMessage.data.user.id)) {
                            return@collect
                        }

                        if (pubSubMessage.data.reward.requiresUserInput) {
                            knownRewards[pubSubMessage.data.id] = pubSubMessage
                        } else {
                            val message = runCatching {
                                PointRedemptionMessage
                                    .parsePointReward(pubSubMessage.timestamp, pubSubMessage.data)
                                    .applyIgnores()
                                    ?.calculateHighlightState()
                            }.getOrNull() ?: return@collect

                            messages[pubSubMessage.channelName]?.update {
                                it.addAndLimit(ChatItem(message), scrollBackLength)
                            }
                        }
                    }

                    is PubSubMessage.Whisper         -> {
                        if (ignoresRepository.isUserBlocked(pubSubMessage.data.userId)) {
                            return@collect
                        }

                        val message = runCatching {
                            WhisperMessage.fromPubSub(pubSubMessage.data)
                                .applyIgnores()
                                ?.calculateHighlightState()
                                ?.parseEmotesAndBadges() as? WhisperMessage
                        }.getOrNull() ?: return@collect

                        val item = ChatItem(message, isMentionTab = true)
                        _whispers.update { current ->
                            current.addAndLimit(item, scrollBackLength)
                        }

                        if (pubSubMessage.data.userId == userState.value.userId) {
                            return@collect
                        }

                        val userForSuggestion = message.displayName.takeIf { it.equals(message.name, ignoreCase = true) } ?: message.name
                        val currentUsers = users[GLOBAL_CHANNEL_TAG] ?: createUserCache()
                        currentUsers.put(message.name.lowercase(), userForSuggestion)
                        usersFlows[GLOBAL_CHANNEL_TAG]?.update {
                            currentUsers.snapshot().values.toSet()
                        }
                        _channelMentionCount.increment(WHISPER_CHANNEL_TAG, 1)
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
    val hasMentions = channelMentionCount.map { it.any { channel -> channel.key != WHISPER_CHANNEL_TAG && channel.value > 0 } }
    val hasWhispers = channelMentionCount.map { it.getOrDefault(WHISPER_CHANNEL_TAG, 0) > 0 }
    val mentions: StateFlow<List<ChatItem>> = _mentions
    val whispers: StateFlow<List<ChatItem>> = _whispers
    val activeChannel: StateFlow<String> = _activeChannel.asStateFlow()
    val channels: StateFlow<List<String>?> = _channels.asStateFlow()
    val chatLoadingFailures = _chatLoadingFailures.asStateFlow()

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

    fun clearChatLoadingFailures() = _chatLoadingFailures.update { emptySet() }

    suspend fun getLatestValidUserState(minChannelsSize: Int = 0): UserState = userState
        .filter {
            it.userId.isNotBlank() && it.followerEmoteSets.size >= minChannelsSize
        }.take(count = 1).single()

    suspend fun loadRecentMessagesIfEnabled(channel: String) {
        when {
            dankChatPreferenceStore.shouldLoadHistory -> loadRecentMessages(channel)
            else                                      -> messages[channel]?.update { current ->
                val message = SystemMessageType.NoHistoryLoaded.toChatItem()
                listOf(message).addAndLimit(current, scrollBackLength, checkForDuplications = true)
            }
        }
    }

    suspend fun loadChatters(channel: String) = withContext(Dispatchers.Default) {
        measureTimeMillis {
            chattersApiClient.getChatters(channel)
                .getOrEmitFailure { ChatLoadingStep.Chatters(channel) }
                ?.let { chatters ->
                    val currentUsers = users
                        .getOrPut(channel) { createUserCache() }
                        .also { cache ->
                            val keys = cache.snapshot().keys
                            chatters.total.forEach { user ->
                                val key = user.lowercase()
                                if (key !in keys) {
                                    cache.put(key, user)
                                }
                            }
                        }

                    usersFlows
                        .getOrPut(channel) { MutableStateFlow(emptySet()) }
                        .update { currentUsers.snapshot().values.toSet() }
                }
        }.let { Log.i(TAG, "Loading chatters for #$channel took $it ms") }
    }

    suspend fun reparseAllEmotesAndBadges() = withContext(Dispatchers.Default) {
        messages.values.map { flow ->
            async {
                flow.update { messages ->
                    messages.map {
                        it.copy(tag = it.tag + 1, message = it.message.parseEmotesAndBadges())
                    }
                }
            }
        }.awaitAll()
    }

    fun setActiveChannel(channel: String) {
        _activeChannel.value = channel
    }

    fun clearMentionCount(channel: String) = with(_channelMentionCount) {
        tryEmit(firstValue.apply { set(channel, 0) })
    }

    fun clearMentionCounts() = with(_channelMentionCount) {
        tryEmit(firstValue.apply { keys.forEach { if (it != WHISPER_CHANNEL_TAG) set(it, 0) } })
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
            if (!firstValue.contains(WHISPER_CHANNEL_TAG)) tryEmit(firstValue.apply { set(channel, 0) })
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
        // TODO check for disconnected and exchange with "Reconnected" to reduce message spam
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
        dankChatPreferenceStore.displayName = name
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

        val userId = ircMessage.tags["user-id"]
        if (ignoresRepository.isUserBlocked(userId)) {
            return
        }

        val userState = userState.value
        val message = runCatching {
            WhisperMessage.parseFromIrc(ircMessage, userState.displayName, userState.color)
                .applyIgnores()
                ?.calculateHighlightState()
                ?.parseEmotesAndBadges() as? WhisperMessage
        }.getOrNull() ?: return

        val userForSuggestion = message.displayName.takeIf { it.equals(message.name, ignoreCase = true) } ?: message.name
        val currentUsers = users
            .getOrPut(GLOBAL_CHANNEL_TAG) { createUserCache() }
            .also { it.put(message.name.lowercase(), userForSuggestion) }

        usersFlows
            .getOrPut(GLOBAL_CHANNEL_TAG) { MutableStateFlow(emptySet()) }
            .update { currentUsers.snapshot().values.toSet() }

        val item = ChatItem(message, isMentionTab = true)
        _whispers.update { current ->
            current.addAndLimit(item, scrollBackLength)
        }
        _channelMentionCount.increment(WHISPER_CHANNEL_TAG, 1)
    }

    private suspend fun handleMessage(ircMessage: IrcMessage) {
        val userId = ircMessage.tags["user-id"]
        if (ignoresRepository.isUserBlocked(userId)) {
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

        val message = runCatching {
            Message.parse(ircMessage)
                ?.applyIgnores()
                ?.calculateHighlightState()
                ?.parseEmotesAndBadges()
        }.getOrElse {
            Log.e(TAG, "Failed to parse message", it)
            return
        } ?: return

        if (message is NoticeMessage && message.channel == GLOBAL_CHANNEL_TAG) {
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

            val userForSuggestion = message.displayName.takeIf { it.equals(message.name, ignoreCase = true) } ?: message.name
            val currentUsers = users
                .getOrPut(message.channel) { createUserCache() }
                .also { cache -> cache.put(message.name.lowercase(), userForSuggestion) }
            usersFlows
                .getOrPut(message.channel) { MutableStateFlow(emptySet()) }
                .update { currentUsers.snapshot().values.toSet() }
        }

        val items = buildList {
            if (message is UserNoticeMessage && message.childMessage != null) {
                add(ChatItem(message.childMessage))
            }
            add(ChatItem(message))
        }

        val channel = when (message) {
            is PrivMessage       -> message.channel
            is UserNoticeMessage -> message.channel
            is NoticeMessage     -> message.channel
            else                 -> return
        }

        messages[channel]?.update { current ->
            current.addAndLimit(additionalMessages + items, scrollBackLength)
        }

        _notificationsFlow.tryEmit(items)
        val mentions = items
            .filter { it.message.highlights.hasMention() }
            .toMentionTabItems()

        if (mentions.isNotEmpty()) {
            _mentions.update { current ->
                current.addAndLimit(mentions, scrollBackLength)
            }
        }

        if (channel != activeChannel.value) {
            if (mentions.isNotEmpty()) {
                _channelMentionCount.increment(channel, mentions.size)
            }

            if (message is PrivMessage) {
                val isUnread = _unreadMessagesMap.firstValue[channel] ?: false
                if (!isUnread) {
                    _unreadMessagesMap.assign(channel, true)
                }
            }
        }
    }

    private fun createUserCache(): LruCache<String, String> = LruCache(USER_CACHE_SIZE)

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

        val result = recentMessagesApiClient.getRecentMessages(channel).getOrElse { throwable ->
            handleRecentMessagesFailure(throwable, channel)
            return@withContext
        }

        loadedRecentsInChannels += channel
        val recentMessages = result.messages.orEmpty()
        val items = mutableListOf<ChatItem>()
        val userSuggestions = mutableListOf<Pair<String, String>>()
        measureTimeMillis {
            for (recentMessage in recentMessages) {
                val parsedIrc = IrcMessage.parse(recentMessage)
                val isCleared = parsedIrc.tags["rm-deleted"] == "1"
                if (ignoresRepository.isUserBlocked(parsedIrc.tags["user-id"])) {
                    continue
                }

                val message = runCatching {
                    Message.parse(parsedIrc)
                        ?.applyIgnores()
                        ?.calculateHighlightState()
                        ?.parseEmotesAndBadges()
                }.getOrNull() ?: continue

                if (message is PrivMessage) {
                    val userForSuggestion = message.displayName.takeIf { it.equals(message.name, ignoreCase = true) } ?: message.name
                    userSuggestions += message.name.lowercase() to userForSuggestion
                }

                if (message is UserNoticeMessage && message.childMessage != null) {
                    items += ChatItem(message.childMessage, isCleared = isCleared)
                }
                items += ChatItem(message, isCleared = isCleared)
            }
        }.let { Log.i(TAG, "Parsing message history for #$channel took $it ms") }

        messages[channel]?.update { current ->
            val withIncompleteWarning = when {
                recentMessages.isNotEmpty() && result.errorCode == RecentMessagesDto.ERROR_CHANNEL_NOT_JOINED -> {
                    current + ChatItem(SystemMessage(SystemMessageType.MessageHistoryIncomplete))
                }

                else                                                                                          -> current
            }
            items.addAndLimit(withIncompleteWarning, scrollBackLength, checkForDuplications = true)
        }

        val mentions = items.filter { (it.message.highlights.hasMention()) }.toMentionTabItems()
        _mentions.update { current ->
            (current + mentions)
                .distinctBy { it.message.id }
                .sortedBy { it.message.timestamp }
        }
        val currentUsers = users
            .getOrPut(channel) { createUserCache() }
            .also { cache ->
                userSuggestions.forEach { (key, value) -> cache.put(key, value) }
            }
        usersFlows
            .getOrPut(channel) { MutableStateFlow(emptySet()) }
            .update { currentUsers.snapshot().values.toSet() }
    }

    private fun handleRecentMessagesFailure(throwable: Throwable, channel: String) {
        val type = when (throwable) {
            !is RecentMessagesApiException -> {
                _chatLoadingFailures.update { it + ChatLoadingFailure(ChatLoadingStep.RecentMessages(channel), throwable) }
                SystemMessageType.MessageHistoryUnavailable(status = null)
            }

            else                           -> when (throwable.error) {
                RecentMessagesError.ChannelNotJoined -> {
                    loadedRecentsInChannels += channel // not a temporary error, so we don't want to retry
                    SystemMessageType.MessageHistoryIgnored
                }

                else                                 -> {
                    _chatLoadingFailures.update { it + ChatLoadingFailure(ChatLoadingStep.RecentMessages(channel), throwable) }
                    SystemMessageType.MessageHistoryUnavailable(status = throwable.status.value.toString())
                }
            }
        }
        makeAndPostSystemMessage(type, setOf(channel))
    }

    private inline fun <T> Result<T>.getOrEmitFailure(step: () -> ChatLoadingStep): T? = getOrElse { throwable ->
        _chatLoadingFailures.update { it + ChatLoadingFailure(step(), throwable) }
        null
    }

    private fun Message.applyIgnores(): Message? = ignoresRepository.applyIgnores(this)
    private fun Message.calculateHighlightState(): Message = highlightsRepository.calculateHighlightState(this)
    private fun Message.parseEmotesAndBadges(): Message = emoteRepository.parseEmotesAndBadges(this)

    companion object {
        private val TAG = ChatRepository::class.java.simpleName
        private val INVISIBLE_CHAR = 0x000E0000.codePointAsString
        private val ESCAPE_TAG = 0x000E0002.codePointAsString
        private const val USER_CACHE_SIZE = 5000
        private const val PUBSUB_TIMEOUT = 1000L
        private const val GLOBAL_CHANNEL_TAG = "*"
        private const val WHISPER_CHANNEL_TAG = "w"

        val ESCAPE_TAG_REGEX = "(?<!$ESCAPE_TAG)$ESCAPE_TAG".toRegex()
        const val ZERO_WIDTH_JOINER = 0x200D.toChar().toString()
    }
}