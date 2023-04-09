package com.flxrs.dankchat.data.repo.chat

import android.graphics.Color
import android.util.Log
import androidx.collection.LruCache
import com.flxrs.dankchat.chat.ChatImportance
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.chat.toMentionTabItems
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.chatters.ChattersApiClient
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApiClient
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApiException
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesError
import com.flxrs.dankchat.data.api.recentmessages.dto.RecentMessagesDto
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.repo.HighlightsRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.data.repo.RepliesRepository
import com.flxrs.dankchat.data.repo.UserDisplayRepository
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.chat.ChatConnection
import com.flxrs.dankchat.data.twitch.chat.ChatEvent
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.NoticeMessage
import com.flxrs.dankchat.data.twitch.message.PointRedemptionMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.hasMention
import com.flxrs.dankchat.data.twitch.message.toChatItem
import com.flxrs.dankchat.data.twitch.pubsub.PubSubManager
import com.flxrs.dankchat.data.twitch.pubsub.PubSubMessage
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.di.ReadConnection
import com.flxrs.dankchat.di.WriteConnection
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.INVISIBLE_CHAR
import com.flxrs.dankchat.utils.extensions.addAndLimit
import com.flxrs.dankchat.utils.extensions.addSystemMessage
import com.flxrs.dankchat.utils.extensions.assign
import com.flxrs.dankchat.utils.extensions.clear
import com.flxrs.dankchat.utils.extensions.codePointAsString
import com.flxrs.dankchat.utils.extensions.firstValue
import com.flxrs.dankchat.utils.extensions.increment
import com.flxrs.dankchat.utils.extensions.mutableSharedFlowOf
import com.flxrs.dankchat.utils.extensions.replaceOrAddHistoryModerationMessage
import com.flxrs.dankchat.utils.extensions.replaceOrAddModerationMessage
import com.flxrs.dankchat.utils.extensions.replaceWithTimeout
import com.flxrs.dankchat.utils.extensions.withoutInvisibleChar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.system.measureTimeMillis

@Singleton
class ChatRepository @Inject constructor(
    private val recentMessagesApiClient: RecentMessagesApiClient,
    private val chattersApiClient: ChattersApiClient,
    private val emoteRepository: EmoteRepository,
    private val highlightsRepository: HighlightsRepository,
    private val ignoresRepository: IgnoresRepository,
    private val userDisplayRepository: UserDisplayRepository,
    private val repliesRepository: RepliesRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val pubSubManager: PubSubManager,
    @ReadConnection private val readConnection: ChatConnection,
    @WriteConnection private val writeConnection: ChatConnection,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _activeChannel = MutableStateFlow<UserName?>(null)
    private val _channels = MutableStateFlow<List<UserName>?>(null)

    private val _notificationsFlow = MutableSharedFlow<List<ChatItem>>(replay = 0, extraBufferCapacity = 10)
    private val _channelMentionCount = mutableSharedFlowOf(mutableMapOf<UserName, Int>())
    private val _unreadMessagesMap = mutableSharedFlowOf(mutableMapOf<UserName, Boolean>())
    private val messages = ConcurrentHashMap<UserName, MutableStateFlow<List<ChatItem>>>()
    private val _mentions = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _whispers = MutableStateFlow<List<ChatItem>>(emptyList())
    private val connectionState = ConcurrentHashMap<UserName, MutableStateFlow<ConnectionState>>()
    private val roomStates = ConcurrentHashMap<UserName, RoomState>()
    private val roomStateFlows = ConcurrentHashMap<UserName, MutableSharedFlow<RoomState>>()
    private val users = ConcurrentHashMap<UserName, LruCache<UserName, DisplayName>>()
    private val usersFlows = ConcurrentHashMap<UserName, MutableStateFlow<Set<DisplayName>>>()
    private val userState = MutableStateFlow(UserState())
    private val _chatLoadingFailures = MutableStateFlow(emptySet<ChatLoadingFailure>())

    private var lastMessage = ConcurrentHashMap<UserName, String>()
    private val loadedRecentsInChannels = mutableSetOf<UserName>()
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
                                    ?.calculateUserDisplays()
                            }.getOrNull() ?: return@collect

                            messages[pubSubMessage.channelName]?.update {
                                it.addAndLimit(ChatItem(message), scrollBackLength, ::onMessageRemoved)
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
                                ?.calculateUserDisplays()
                                ?.parseEmotesAndBadges() as? WhisperMessage
                        }.getOrNull() ?: return@collect

                        val item = ChatItem(message, isMentionTab = true)
                        _whispers.update { current ->
                            current.addAndLimit(item, scrollBackLength, ::onMessageRemoved)
                        }

                        if (pubSubMessage.data.userId == userState.value.userId) {
                            return@collect
                        }

                        val userForSuggestion = message.name.valueOrDisplayName(message.displayName).toDisplayName()
                        val currentUsers = users[GLOBAL_CHANNEL_TAG] ?: createUserCache()
                        currentUsers.put(message.name.lowercase(), userForSuggestion)
                        usersFlows[GLOBAL_CHANNEL_TAG]?.update {
                            currentUsers.snapshot().values.toSet()
                        }
                        _channelMentionCount.increment(WhisperMessage.WHISPER_CHANNEL, 1)
                        _notificationsFlow.tryEmit(listOf(item))
                    }

                    is PubSubMessage.ModeratorAction -> {
                        val (timestamp, channelId, data) = pubSubMessage
                        val channelName = roomStates.values.find { state -> state.channelId == channelId }?.channel ?: return@collect
                        val message = runCatching {
                            ModerationMessage.parseModerationAction(timestamp, channelName, data)
                        }.getOrElse {
                            return@collect
                        }

                        messages[message.channel]?.update { current ->
                            when (message.action) {
                                ModerationMessage.Action.Delete -> current.replaceWithTimeout(message, scrollBackLength, ::onMessageRemoved)
                                else                            -> current.replaceOrAddModerationMessage(message, scrollBackLength, ::onMessageRemoved)
                            }
                        }
                    }
                }
            }
        }
    }

    val notificationsFlow: SharedFlow<List<ChatItem>> = _notificationsFlow.asSharedFlow()
    val channelMentionCount: SharedFlow<Map<UserName, Int>> = _channelMentionCount.asSharedFlow()
    val unreadMessagesMap: SharedFlow<Map<UserName, Boolean>> = _unreadMessagesMap.asSharedFlow()
    val userStateFlow: StateFlow<UserState> = userState.asStateFlow()
    val hasMentions = channelMentionCount.map { it.any { channel -> channel.key != WhisperMessage.WHISPER_CHANNEL && channel.value > 0 } }
    val hasWhispers = channelMentionCount.map { it.getOrDefault(WhisperMessage.WHISPER_CHANNEL, 0) > 0 }
    val mentions: StateFlow<List<ChatItem>> = _mentions
    val whispers: StateFlow<List<ChatItem>> = _whispers
    val activeChannel: StateFlow<UserName?> = _activeChannel.asStateFlow()
    val channels: StateFlow<List<UserName>?> = _channels.asStateFlow()
    val chatLoadingFailures = _chatLoadingFailures.asStateFlow()

    var scrollBackLength = 500
        set(value) {
            messages.forEach { (_, messagesFlow) ->
                if (messagesFlow.value.size > scrollBackLength) {
                    messagesFlow.update {
                        it.takeLast(value)
                    }
                }
            }
            field = value
        }

    fun getChat(channel: UserName): StateFlow<List<ChatItem>> = messages.getOrPut(channel) { MutableStateFlow(emptyList()) }
    fun getConnectionState(channel: UserName): StateFlow<ConnectionState> = connectionState.getOrPut(channel) { MutableStateFlow(ConnectionState.DISCONNECTED) }
    fun getRoomState(channel: UserName): SharedFlow<RoomState> = roomStateFlows.getOrPut(channel) { MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    fun getUsers(channel: UserName): StateFlow<Set<DisplayName>> = usersFlows.getOrPut(channel) { MutableStateFlow(emptySet()) }

    fun isModeratorInChannel(channel: UserName?): Boolean = channel != null && channel in userStateFlow.value.moderationChannels
    fun findMessage(messageId: String, channel: UserName?) = (channel?.let { messages[channel] } ?: whispers).value.find { it.message.id == messageId }?.message
    fun findDisplayName(channel: UserName, userName: UserName): DisplayName? = users[channel]?.get(userName)

    fun clearChatLoadingFailures() = _chatLoadingFailures.update { emptySet() }

    suspend fun getLatestValidUserState(minChannelsSize: Int = 0): UserState = userState
        .filter {
            it.userId != null && it.userId.value.isNotBlank() && it.globalEmoteSets.isNotEmpty() && it.followerEmoteSets.size >= minChannelsSize
        }.take(count = 1).single()

    fun clearUserStateEmotes() = userState.update { it.copy(globalEmoteSets = emptyList(), followerEmoteSets = emptyMap()) }

    suspend fun loadRecentMessagesIfEnabled(channel: UserName) {
        when {
            dankChatPreferenceStore.shouldLoadHistory -> loadRecentMessages(channel)
            else                                      -> messages[channel]?.update { current ->
                val message = SystemMessageType.NoHistoryLoaded.toChatItem()
                listOf(message).addAndLimit(current, scrollBackLength, ::onMessageRemoved, checkForDuplications = true)
            }
        }
    }

    suspend fun loadChatters(channel: UserName): Unit = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() > CHATTERS_SUNSET_MILLIS) {
            return@withContext
        }

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
                                    cache.put(key, user.toDisplayName())
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
                        it.copy(
                            tag = it.tag + 1,
                            message = it.message
                                .parseEmotesAndBadges()
                                .updateMessageInThread(),
                        )
                    }
                }
            }
        }.awaitAll()
    }

    fun setActiveChannel(channel: UserName?) {
        _activeChannel.value = channel
    }

    fun clearMentionCount(channel: UserName) = with(_channelMentionCount) {
        tryEmit(firstValue.apply { set(channel, 0) })
    }

    fun clearMentionCounts() = with(_channelMentionCount) {
        tryEmit(firstValue.apply { keys.forEach { if (it != WhisperMessage.WHISPER_CHANNEL) set(it, 0) } })
    }

    fun clearUnreadMessage(channel: UserName) {
        _unreadMessagesMap.assign(channel, false)
    }

    fun clear(channel: UserName) {
        messages[channel]?.value = emptyList()
    }

    fun closeAndReconnect() {
        val channels = channels.value.orEmpty()

        readConnection.close()
        writeConnection.close()
        pubSubManager.close()
        connectAndJoin(channels)
    }

    fun reconnect(reconnectPubsub: Boolean = true) {
        readConnection.reconnect()
        writeConnection.reconnect()

        if (reconnectPubsub) {
            pubSubManager.reconnect()
        }
    }

    fun reconnectIfNecessary() {
        readConnection.reconnectIfNecessary()
        writeConnection.reconnectIfNecessary()
        pubSubManager.reconnectIfNecessary()
    }

    fun getLastMessage(): String? = lastMessage[activeChannel.value]?.withoutInvisibleChar

    fun fakeWhisperIfNecessary(input: String) {
        if (pubSubManager.connectedAndHasWhisperTopic) {
            return
        }
        // fake whisper handling
        val split = input.split(" ")
        if (split.size > 2 && (split[0] == "/w" || split[0] == ".w") && split[1].isNotBlank()) {
            val message = input.substring(4 + split[1].length)
            val emotes = emoteRepository.parse3rdPartyEmotes(message, WhisperMessage.WHISPER_CHANNEL, withTwitch = true)
            val userState = userState.value
            val name = dankChatPreferenceStore.userName ?: return
            val displayName = userState.displayName ?: return
            val fakeMessage = WhisperMessage(
                userId = userState.userId,
                name = name,
                displayName = displayName,
                color = userState.color?.let(Color::parseColor) ?: Message.DEFAULT_COLOR,
                recipientId = null,
                recipientColor = Message.DEFAULT_COLOR,
                recipientName = split[1].toUserName(),
                recipientDisplayName = split[1].toDisplayName(),
                message = message,
                rawEmotes = "",
                rawBadges = "",
                emotes = emotes,
            )
            val fakeItem = ChatItem(fakeMessage, isMentionTab = true)
            _whispers.update {
                it.addAndLimit(fakeItem, scrollBackLength, ::onMessageRemoved)
            }
        }
    }

    fun sendMessage(input: String, replyId: String? = null) {
        val channel = activeChannel.value ?: return
        val preparedMessage = prepareMessage(channel, input, replyId) ?: return
        writeConnection.sendMessage(preparedMessage)
    }

    fun connectAndJoin(channels: List<UserName> = dankChatPreferenceStore.channels) {
        if (!pubSubManager.connected) {
            pubSubManager.start()
        }

        if (!readConnection.connected) {
            connect()
            joinChannels(channels)
        }
    }

    fun joinChannel(channel: UserName, listenToPubSub: Boolean = true): List<UserName> {
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

    fun createFlowsIfNecessary(channel: UserName) {
        messages.putIfAbsent(channel, MutableStateFlow(emptyList()))
        connectionState.putIfAbsent(channel, MutableStateFlow(ConnectionState.DISCONNECTED))
        roomStateFlows.putIfAbsent(channel, MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST))
        users.putIfAbsent(channel, createUserCache())
        usersFlows.putIfAbsent(channel, MutableStateFlow(emptySet()))

        with(_channelMentionCount) {
            if (!firstValue.contains(WhisperMessage.WHISPER_CHANNEL)) tryEmit(firstValue.apply { set(channel, 0) })
            if (!firstValue.contains(channel)) tryEmit(firstValue.apply { set(channel, 0) })
        }
    }

    fun updateChannels(updatedChannels: List<UserName>) {
        val currentChannels = channels.value.orEmpty()
        val removedChannels = currentChannels - updatedChannels.toSet()

        removedChannels.forEach {
            partChannel(it)
        }

        _channels.value = updatedChannels
    }

    fun appendLastMessage(channel: UserName, message: String) {
        lastMessage[channel] = message
    }

    private fun connect() {
        readConnection.connect()
        writeConnection.connect()
    }

    private fun joinChannels(channels: List<UserName>) {
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

    private fun partChannel(channel: UserName, unListenFromPubSub: Boolean = true): List<UserName> {
        val updatedChannels = channels.value.orEmpty() - channel
        _channels.value = updatedChannels

        removeChannelData(channel)
        readConnection.partChannel(channel)

        if (unListenFromPubSub) {
            pubSubManager.removeChannel(channel)
        }

        return updatedChannels
    }

    private fun removeChannelData(channel: UserName) {
        messages.remove(channel)
        usersFlows.remove(channel)
        connectionState.remove(channel)
        roomStateFlows.remove(channel)
        roomStates.remove(channel)
        users.remove(channel)
        lastMessage.remove(channel)
        _channelMentionCount.clear(channel)
        loadedRecentsInChannels.remove(channel)
        emoteRepository.removeChannel(channel)
        repliesRepository.cleanupMessageThreadsInChannel(channel)
    }

    private fun prepareMessage(channel: UserName, message: String, replyId: String?): String? {
        if (message.isBlank()) return null
        val trimmedMessage = message.trimEnd()
        val replyIdOrBlank = replyId?.let { "@reply-parent-msg-id=$it " }.orEmpty()

        val messageWithSuffix = when (lastMessage[channel].orEmpty()) {
            trimmedMessage -> when {
                trimmedMessage.endsWith(INVISIBLE_CHAR) -> trimmedMessage.withoutInvisibleChar
                else                                    -> "$trimmedMessage $INVISIBLE_CHAR"
            }

            else           -> trimmedMessage
        }

        val messageWithEmojiFix = messageWithSuffix.replace(ZERO_WIDTH_JOINER, ESCAPE_TAG)
        lastMessage[channel] = messageWithEmojiFix
        return "${replyIdOrBlank}PRIVMSG #$channel :$messageWithEmojiFix"
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

    private fun handleConnected(channel: UserName, isAnonymous: Boolean) {
        val state = when {
            isAnonymous -> ConnectionState.CONNECTED_NOT_LOGGED_IN
            else        -> ConnectionState.CONNECTED
        }
        makeAndPostSystemMessage(state.toSystemMessageType(), setOf(channel))
        connectionState[channel]?.value = state
    }

    private fun handleClearChat(msg: IrcMessage) {
        val parsed = runCatching {
            ModerationMessage.parseClearChat(msg)
        }.getOrElse {
            return
        }

        messages[parsed.channel]?.update { current ->
            current.replaceOrAddModerationMessage(parsed, scrollBackLength, ::onMessageRemoved)
        }
    }

    private fun handleRoomState(msg: IrcMessage) {
        val channel = msg.params.getOrNull(0)?.substring(1)?.toUserName() ?: return
        val channelId = msg.tags["room-id"]?.toUserId() ?: return
        val flow = roomStateFlows[channel] ?: return
        val state = if (flow.replayCache.isEmpty()) {
            RoomState(channel, channelId).copyFromIrcMessage(msg)
        } else {
            flow.firstValue.copyFromIrcMessage(msg)
        }
        roomStates[channel] = state
        flow.tryEmit(state)
    }

    private fun handleGlobalUserState(msg: IrcMessage) {
        val id = msg.tags["user-id"]?.toUserId()
        val sets = msg.tags["emote-sets"]?.split(",")
        val color = msg.tags["color"]
        val name = msg.tags["display-name"]?.toDisplayName()
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
        val channel = msg.params.getOrNull(0)?.substring(1)?.toUserName() ?: return
        val id = msg.tags["user-id"]?.toUserId()
        val sets = msg.tags["emote-sets"]?.split(",").orEmpty()
        val color = msg.tags["color"]
        val name = msg.tags["display-name"]?.toDisplayName()
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
        val parsed = runCatching {
            ModerationMessage.parseClearMessage(msg)
        }.getOrElse {
            return
        }

        messages[parsed.channel]?.update { current ->
            current.replaceWithTimeout(parsed, scrollBackLength, ::onMessageRemoved)
        }
    }

    private fun handleWhisper(ircMessage: IrcMessage) {
        if (pubSubManager.connectedAndHasWhisperTopic) {
            return
        }

        val userId = ircMessage.tags["user-id"]?.toUserId()
        if (ignoresRepository.isUserBlocked(userId)) {
            return
        }

        val userState = userState.value
        val recipient = userState.displayName ?: return
        val message = runCatching {
            WhisperMessage.parseFromIrc(ircMessage, recipient, userState.color)
                .applyIgnores()
                ?.calculateHighlightState()
                ?.calculateUserDisplays()
                ?.parseEmotesAndBadges() as? WhisperMessage
        }.getOrNull() ?: return

        val userForSuggestion = message.name.valueOrDisplayName(message.displayName).toDisplayName()
        val currentUsers = users
            .getOrPut(GLOBAL_CHANNEL_TAG) { createUserCache() }
            .also { it.put(message.name.lowercase(), userForSuggestion) }

        usersFlows
            .getOrPut(GLOBAL_CHANNEL_TAG) { MutableStateFlow(emptySet()) }
            .update { currentUsers.snapshot().values.toSet() }

        val item = ChatItem(message, isMentionTab = true)
        _whispers.update { current ->
            current.addAndLimit(item, scrollBackLength, ::onMessageRemoved)
        }
        _channelMentionCount.increment(WhisperMessage.WHISPER_CHANNEL, 1)
    }

    private suspend fun handleMessage(ircMessage: IrcMessage) {
        val userId = ircMessage.tags["user-id"]?.toUserId()
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
                ?.calculateMessageThread { channel, id -> messages[channel]?.value?.find { it.message.id == id }?.message }
                ?.calculateHighlightState()
                ?.calculateUserDisplays()
                ?.parseEmotesAndBadges()
                ?.updateMessageInThread()
        }.getOrElse {
            Log.e(TAG, "Failed to parse message", it)
            return
        } ?: return

        if (message is NoticeMessage && message.channel == GLOBAL_CHANNEL_TAG) {
            messages.keys.forEach {
                messages[it]?.update { current ->
                    current.addAndLimit(ChatItem(message, importance = ChatImportance.SYSTEM), scrollBackLength, ::onMessageRemoved)
                }
            }
            return
        }

        if (message is PrivMessage) {
            if (message.name == dankChatPreferenceStore.userName) {
                val previousLastMessage = lastMessage[message.channel].orEmpty()
                val lastMessageWasCommand = previousLastMessage.startsWith('.') || previousLastMessage.startsWith('/')
                if (!lastMessageWasCommand && previousLastMessage.withoutInvisibleChar != message.originalMessage.withoutInvisibleChar) {
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

            val userForSuggestion = message.name.valueOrDisplayName(message.displayName).toDisplayName()
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
            val importance = when (message) {
                is NoticeMessage -> ChatImportance.SYSTEM
                else             -> ChatImportance.REGULAR
            }
            add(ChatItem(message, importance = importance))
        }

        val channel = when (message) {
            is PrivMessage       -> message.channel
            is UserNoticeMessage -> message.channel
            is NoticeMessage     -> message.channel
            else                 -> return
        }

        messages[channel]?.update { current ->
            current.addAndLimit(items = additionalMessages + items, scrollBackLength, ::onMessageRemoved)
        }

        _notificationsFlow.tryEmit(items)
        val mentions = items
            .filter { it.message.highlights.hasMention() }
            .toMentionTabItems()

        if (mentions.isNotEmpty()) {
            _mentions.update { current ->
                current.addAndLimit(mentions, scrollBackLength, ::onMessageRemoved)
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

    private fun createUserCache(): LruCache<UserName, DisplayName> = LruCache(USER_CACHE_SIZE)

    fun makeAndPostCustomSystemMessage(message: String, channel: UserName) {
        messages[channel]?.update {
            it.addSystemMessage(SystemMessageType.Custom(message), scrollBackLength, ::onMessageRemoved)
        }
    }

    fun makeAndPostSystemMessage(type: SystemMessageType, channel: UserName) {
        messages[channel]?.update {
            it.addSystemMessage(type, scrollBackLength, ::onMessageRemoved)
        }
    }

    private fun makeAndPostSystemMessage(type: SystemMessageType, channels: Set<UserName> = messages.keys) {
        channels.forEach { channel ->
            val flow = messages[channel] ?: return@forEach
            val current = flow.value
            flow.value = current.addSystemMessage(type, scrollBackLength, ::onMessageRemoved) {
                if (dankChatPreferenceStore.shouldLoadMessagesOnReconnect) {
                    scope.launch { loadRecentMessages(channel, isReconnect = true) }
                }
            }
        }
    }

    private fun ConnectionState.toSystemMessageType(): SystemMessageType = when (this) {
        ConnectionState.DISCONNECTED            -> SystemMessageType.Disconnected
        ConnectionState.CONNECTED,
        ConnectionState.CONNECTED_NOT_LOGGED_IN -> SystemMessageType.Connected
    }

    private suspend fun loadRecentMessages(channel: UserName, isReconnect: Boolean = false) = withContext(Dispatchers.IO) {
        if (!isReconnect && channel in loadedRecentsInChannels) {
            return@withContext
        }

        val result = recentMessagesApiClient.getRecentMessages(channel).getOrElse { throwable ->
            if (!isReconnect) {
                handleRecentMessagesFailure(throwable, channel)
            }
            return@withContext
        }

        loadedRecentsInChannels += channel
        val recentMessages = result.messages.orEmpty()
        val items = mutableListOf<ChatItem>()
        val userSuggestions = mutableListOf<Pair<UserName, DisplayName>>()
        measureTimeMillis {
            for (recentMessage in recentMessages) {
                val parsedIrc = IrcMessage.parse(recentMessage)
                val isDeleted = parsedIrc.tags["rm-deleted"] == "1"
                if (ignoresRepository.isUserBlocked(parsedIrc.tags["user-id"]?.toUserId())) {
                    continue
                }

                when (parsedIrc.command) {
                    "CLEARCHAT" -> {
                        val parsed = runCatching {
                            ModerationMessage.parseClearChat(parsedIrc)
                        }.getOrNull() ?: continue

                        items.replaceOrAddHistoryModerationMessage(parsed)
                    }

                    "CLEARMSG"  -> {
                        val parsed = runCatching {
                            ModerationMessage.parseClearMessage(parsedIrc)
                        }.getOrNull() ?: continue

                        items += ChatItem(parsed, importance = ChatImportance.SYSTEM)
                    }

                    else        -> {
                        val message = runCatching {
                            Message.parse(parsedIrc)
                                ?.applyIgnores()
                                ?.calculateMessageThread { _, id -> items.find { it.message.id == id }?.message }
                                ?.calculateHighlightState()
                                ?.calculateUserDisplays()
                                ?.parseEmotesAndBadges()
                                ?.updateMessageInThread()
                        }.getOrNull() ?: continue

                        if (message is PrivMessage) {
                            val userForSuggestion = message.name.valueOrDisplayName(message.displayName).toDisplayName()
                            userSuggestions += message.name.lowercase() to userForSuggestion
                        }

                        val importance = when {
                            isDeleted   -> ChatImportance.DELETED
                            isReconnect -> ChatImportance.SYSTEM
                            else        -> ChatImportance.REGULAR
                        }
                        if (message is UserNoticeMessage && message.childMessage != null) {
                            items += ChatItem(message.childMessage, importance = importance)
                        }
                        items += ChatItem(message, importance = importance)
                    }
                }
            }
        }.let { Log.i(TAG, "Parsing message history for #$channel took $it ms") }

        messages[channel]?.update { current ->
            val withIncompleteWarning = when {
                !isReconnect && recentMessages.isNotEmpty() && result.errorCode == RecentMessagesDto.ERROR_CHANNEL_NOT_JOINED -> {
                    current + SystemMessageType.MessageHistoryIncomplete.toChatItem()
                }

                else                                                                                                          -> current
            }
            withIncompleteWarning.addAndLimit(items, scrollBackLength, ::onMessageRemoved, checkForDuplications = true)
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

    private fun handleRecentMessagesFailure(throwable: Throwable, channel: UserName) {
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
    private fun Message.calculateUserDisplays(): Message = userDisplayRepository.calculateUserDisplay(this)

    private fun Message.calculateMessageThread(findMessageById: (channel: UserName, id: String) -> Message?): Message {
        return repliesRepository.calculateMessageThread(message = this, findMessageById)
    }

    private fun Message.updateMessageInThread(): Message = repliesRepository.updateMessageInThread(this)

    private fun onMessageRemoved(item: ChatItem) = repliesRepository.cleanupMessageThread(item.message)

    companion object {
        private val TAG = ChatRepository::class.java.simpleName
        private val ESCAPE_TAG = 0x000E0002.codePointAsString
        private const val USER_CACHE_SIZE = 5000
        private const val PUBSUB_TIMEOUT = 1000L
        private val GLOBAL_CHANNEL_TAG = UserName("*")
        private const val CHATTERS_SUNSET_MILLIS = 1680541200000L // 2023-04-03 17:00:00

        val ESCAPE_TAG_REGEX = "(?<!$ESCAPE_TAG)$ESCAPE_TAG".toRegex()
        const val ZERO_WIDTH_JOINER = 0x200D.toChar().toString()
    }
}
