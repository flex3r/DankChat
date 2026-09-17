package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.eventapi.AutomodHeld
import com.flxrs.dankchat.data.api.eventapi.AutomodUpdate
import com.flxrs.dankchat.data.api.eventapi.ModerationAction
import com.flxrs.dankchat.data.api.eventapi.SystemMessage
import com.flxrs.dankchat.data.api.eventapi.UserMessageHeld
import com.flxrs.dankchat.data.api.eventapi.UserMessageUpdated
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.AutomodMessageStatus
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.AutomodReasonDto
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.BlockedTermReasonDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.chat.ChatImportance
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.chat.toMentionTabItems
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.repo.PinnedMessageRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.chat.ChatEvent
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.message.AutomodMessage
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.ModerationMessage
import com.flxrs.dankchat.data.twitch.message.NoticeMessage
import com.flxrs.dankchat.data.twitch.message.PointRedemptionMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.UserNoticeMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.data.twitch.message.hasMention
import com.flxrs.dankchat.data.twitch.message.toDebugChatItem
import com.flxrs.dankchat.data.twitch.pubsub.PubSubMessage
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.TextResource
import com.flxrs.dankchat.utils.extensions.codePointSlice
import com.flxrs.dankchat.utils.extensions.runCatchingCancellable
import com.flxrs.dankchat.utils.extensions.withoutInvisibleChar
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger("ChatEventProcessor")
private const val MAX_LAST_MESSAGES = 5

internal data class LastMessage(
    val sent: String,
    val typed: String,
)

@Single
class ChatEventProcessor(
    private val messageProcessor: MessageProcessor,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatConnector: ChatConnector,
    private val chatNotificationRepository: ChatNotificationRepository,
    private val chatChannelProvider: ChatChannelProvider,
    private val recentMessagesHandler: RecentMessagesHandler,
    private val userStateRepository: UserStateRepository,
    private val usersRepository: UsersRepository,
    private val pinnedMessageRepository: PinnedMessageRepository,
    private val authDataStore: AuthDataStore,
    private val channelRepository: ChannelRepository,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val messageRateTracker: ChannelMessageRateTracker,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val _lastMessages = MutableStateFlow<PersistentMap<UserName, PersistentList<LastMessage>>>(persistentMapOf())
    internal val lastMessagesFlow: StateFlow<PersistentMap<UserName, PersistentList<LastMessage>>> = _lastMessages.asStateFlow()
    private val _lastReceivedWhisperUser = MutableStateFlow<UserName?>(null)
    internal val lastReceivedWhisperUser: StateFlow<UserName?> = _lastReceivedWhisperUser.asStateFlow()
    private val knownRewards = ConcurrentHashMap<String, PubSubMessage.PointRedemption>()
    private val knownAutomodHeldIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val rewardMutex = Mutex()

    init {
        scope.launch { collectReadConnectionEvents() }
        scope.launch { collectWriteConnectionEvents() }
        scope.launch { collectPubSubEvents() }
        scope.launch { collectEventSubEvents() }
    }

    fun getLastMessage(channel: UserName): String? = _lastMessages.value[channel]?.firstOrNull()?.sent

    fun getLastMessageForDisplay(channel: UserName?): String? = channel?.let { _lastMessages.value[it]?.firstOrNull()?.typed }

    fun getRecentMessagesForDisplay(channel: UserName?): ImmutableList<String> = when (channel) {
        null -> persistentListOf()

        else -> _lastMessages.value[channel]
            ?.map { it.typed }
            .orEmpty()
            .toImmutableList()
    }

    fun setLastMessage(
        channel: UserName,
        sent: String,
        typed: String = sent,
    ) {
        _lastMessages.update { messages ->
            val updated = (messages[channel] ?: persistentListOf())
                .removingAll { it.typed == typed }
                .addingAt(0, LastMessage(sent = sent, typed = typed))
                .let { it.subList(0, minOf(it.size, MAX_LAST_MESSAGES)).toPersistentList() }
            messages.putting(channel, updated)
        }
    }

    fun removeLastMessages(channel: UserName) {
        _lastMessages.update { it.removing(channel) }
    }

    suspend fun loadRecentMessages(
        channel: UserName,
        isReconnect: Boolean = false,
    ) {
        val result = recentMessagesHandler.load(channel, isReconnect)
        chatNotificationRepository.addMentionsDeduped(result.mentionItems)
        usersRepository.updateUsers(channel, result.userSuggestions)
    }

    private suspend fun collectReadConnectionEvents() {
        chatConnector.readEvents.collect { event ->
            when (event) {
                is ChatEvent.Connected -> handleConnected(event.isAnonymous)
                is ChatEvent.Closed -> handleDisconnect()
                is ChatEvent.ChannelNonExistent -> postSystemMessageAndReconnect(SystemMessageType.ChannelNonExistent(event.channel), setOf(event.channel))
                is ChatEvent.LoginFailed -> postSystemMessageAndReconnect(SystemMessageType.LoginExpired)
                is ChatEvent.Message -> onMessage(event.message)
                is ChatEvent.Error -> handleDisconnect()
            }
        }
    }

    private suspend fun collectWriteConnectionEvents() {
        chatConnector.writeEvents.collect { event ->
            if (event is ChatEvent.Message) {
                onWriterMessage(event.message)
            }
        }
    }

    private suspend fun collectPubSubEvents() {
        chatConnector.pubSubEvents.collect { pubSubMessage ->
            when (pubSubMessage) {
                is PubSubMessage.PointRedemption -> handlePubSubReward(pubSubMessage)
                is PubSubMessage.ModeratorAction -> handlePubSubModeration(pubSubMessage)
                is PubSubMessage.PinnedChatUpdate -> handlePubSubPinnedChatUpdate(pubSubMessage)
            }
        }
    }

    private suspend fun handlePubSubPinnedChatUpdate(pubSubMessage: PubSubMessage.PinnedChatUpdate) {
        when {
            pubSubMessage.removed -> pinnedMessageRepository.clear(pubSubMessage.channelName)
            else -> pinnedMessageRepository.fetch(pubSubMessage.channelName)
        }
    }

    private suspend fun collectEventSubEvents() {
        chatConnector.eventSubEvents.collect { eventMessage ->
            when (eventMessage) {
                is ModerationAction -> handleEventSubModeration(eventMessage)
                is AutomodHeld -> handleAutomodHeld(eventMessage)
                is AutomodUpdate -> handleAutomodUpdate(eventMessage)
                is UserMessageHeld -> handleUserMessageHeld(eventMessage)
                is UserMessageUpdated -> handleUserMessageUpdated(eventMessage)
                is SystemMessage -> postEventSubDebugMessage(eventMessage.message)
            }
        }
    }

    private suspend fun handlePubSubReward(pubSubMessage: PubSubMessage.PointRedemption) {
        if (messageProcessor.isUserBlocked(pubSubMessage.data.user.id)) {
            return
        }

        // Automatic rewards (gigantified emotes, animated messages) are stored
        // for cost lookup but don't create separate PointRedemptionMessages.
        val isAutomaticReward = pubSubMessage.data.reward.rewardType != null
        if (pubSubMessage.data.reward.requiresUserInput || isAutomaticReward) {
            val id = pubSubMessage.data.reward.effectiveId
            rewardMutex.withLock {
                when {
                    knownRewards.containsKey(id) -> {
                        logger.debug { "Removing known reward $id" }
                        knownRewards.remove(id)
                    }

                    else -> {
                        logger.debug { "Received pubsub reward message with id $id" }
                        knownRewards[id] = pubSubMessage
                    }
                }
            }
        } else {
            val message =
                runCatching {
                    messageProcessor.processReward(
                        PointRedemptionMessage.parsePointReward(pubSubMessage.timestamp, pubSubMessage.data),
                    )
                }.getOrNull() ?: return

            chatMessageRepository.addMessages(pubSubMessage.channelName, listOf(ChatItem(message)))
        }
    }

    private fun handlePubSubModeration(pubSubMessage: PubSubMessage.ModeratorAction) {
        val (timestamp, channelId, data) = pubSubMessage
        val channelName = channelRepository.tryGetUserNameById(channelId) ?: return
        val message =
            runCatching {
                ModerationMessage.parseModerationAction(timestamp, channelName, data)
            }.getOrElse { return }

        chatMessageRepository.applyModerationMessage(message)
    }

    private fun handleEventSubModeration(eventMessage: ModerationAction) {
        val (id, timestamp, channelName, data) = eventMessage
        val message =
            runCatching {
                ModerationMessage.parseModerationAction(id, timestamp, channelName, data)
            }.getOrElse {
                logger.debug { "Failed to parse event sub moderation message: $it" }
                return
            }

        chatMessageRepository.applyModerationMessage(message)
    }

    private fun handleAutomodHeld(eventMessage: AutomodHeld) {
        val data = eventMessage.data
        if (!knownAutomodHeldIds.add(data.messageId)) {
            return
        }
        val reason = formatAutomodReason(data.reason, data.automod, data.blockedTerm, data.message.text)
        val userColor = usersRepository.getCachedUserColor(data.userLogin)
        val automodBadge =
            Badge.GlobalBadge(
                title = "AutoMod",
                badgeTag = "automod/1",
                badgeInfo = null,
                url = "",
                type = BadgeType.Authority,
            )
        val automodMsg =
            AutomodMessage(
                timestamp = eventMessage.timestamp.toEpochMilliseconds(),
                id = eventMessage.id,
                channel = eventMessage.channelName,
                heldMessageId = data.messageId,
                userName = data.userLogin,
                userDisplayName = data.userName,
                messageText = data.message.text,
                reason = reason,
                badges = listOf(automodBadge),
                color = userColor,
            )
        chatMessageRepository.addMessages(eventMessage.channelName, listOf(ChatItem(automodMsg, importance = ChatImportance.SYSTEM)))
    }

    private fun handleAutomodUpdate(eventMessage: AutomodUpdate) {
        knownAutomodHeldIds.remove(eventMessage.data.messageId)
        val newStatus =
            when (eventMessage.data.status) {
                AutomodMessageStatus.Approved -> AutomodMessage.Status.Approved
                AutomodMessageStatus.Denied -> AutomodMessage.Status.Denied
                AutomodMessageStatus.Expired -> AutomodMessage.Status.Expired
            }
        chatMessageRepository.updateAutomodMessageStatus(eventMessage.channelName, eventMessage.data.messageId, newStatus)
    }

    private fun handleUserMessageHeld(eventMessage: UserMessageHeld) {
        val data = eventMessage.data
        val automodBadge =
            Badge.GlobalBadge(
                title = "AutoMod",
                badgeTag = "automod/1",
                badgeInfo = null,
                url = "",
                type = BadgeType.Authority,
            )
        val automodMsg =
            AutomodMessage(
                timestamp = eventMessage.timestamp.toEpochMilliseconds(),
                id = eventMessage.id,
                channel = eventMessage.channelName,
                heldMessageId = data.messageId,
                userName = data.userLogin,
                userDisplayName = data.userName,
                messageText = null,
                reason = TextResource.Res(R.string.automod_user_held),
                badges = listOf(automodBadge),
                isUserSide = true,
            )
        chatMessageRepository.addMessages(eventMessage.channelName, listOf(ChatItem(automodMsg, importance = ChatImportance.SYSTEM)))
    }

    private fun handleUserMessageUpdated(eventMessage: UserMessageUpdated) {
        val data = eventMessage.data
        val automodBadge =
            Badge.GlobalBadge(
                title = "AutoMod",
                badgeTag = "automod/1",
                badgeInfo = null,
                url = "",
                type = BadgeType.Authority,
            )
        val reason =
            when (data.status) {
                AutomodMessageStatus.Approved -> TextResource.Res(R.string.automod_user_accepted)
                AutomodMessageStatus.Denied -> TextResource.Res(R.string.automod_user_denied)
                AutomodMessageStatus.Expired -> TextResource.Res(R.string.automod_status_expired)
            }
        val automodMsg =
            AutomodMessage(
                timestamp = eventMessage.timestamp.toEpochMilliseconds(),
                id = eventMessage.id,
                channel = eventMessage.channelName,
                heldMessageId = data.messageId,
                userName = data.userLogin,
                userDisplayName = data.userName,
                messageText = null,
                reason = reason,
                badges = listOf(automodBadge),
                isUserSide = true,
                status =
                    when (data.status) {
                        AutomodMessageStatus.Approved -> AutomodMessage.Status.Approved
                        AutomodMessageStatus.Denied -> AutomodMessage.Status.Denied
                        AutomodMessageStatus.Expired -> AutomodMessage.Status.Expired
                    },
            )
        chatMessageRepository.addMessages(eventMessage.channelName, listOf(ChatItem(automodMsg, importance = ChatImportance.SYSTEM)))
    }

    private suspend fun onMessage(msg: IrcMessage) {
        when (msg.command) {
            "CLEARCHAT" -> handleClearChat(msg)

            "CLEARMSG" -> handleClearMsg(msg)

            "ROOMSTATE" -> channelRepository.handleRoomState(msg)

            "USERSTATE" -> userStateRepository.handleUserState(msg)

            "GLOBALUSERSTATE" -> userStateRepository.handleGlobalUserState(msg)

            "WHISPER" -> handleWhisper(msg)

            "PRIVMSG" -> {
                msg.params.firstOrNull()?.let { messageRateTracker.onMessage(it.substring(1).toUserName()) }
                handleMessage(msg)
            }

            else -> handleMessage(msg)
        }
    }

    private suspend fun onWriterMessage(message: IrcMessage) {
        when (message.command) {
            "USERSTATE" -> userStateRepository.handleUserState(message)
            "GLOBALUSERSTATE" -> userStateRepository.handleGlobalUserState(message)
            "NOTICE" -> handleMessage(message)
        }
    }

    private fun handleDisconnect() {
        val state = ConnectionState.DISCONNECTED
        chatConnector.setAllConnectionStates(state)
        postSystemMessageAndReconnect(state.toSystemMessageType())
    }

    private fun handleConnected(isAnonymous: Boolean) {
        val state =
            when {
                isAnonymous -> ConnectionState.CONNECTED_NOT_LOGGED_IN
                else -> ConnectionState.CONNECTED
            }
        val transitioning =
            chatChannelProvider.channels.value
                .orEmpty()
                .filter { chatConnector.getConnectionState(it).value != state }
                .toSet()

        chatConnector.setAllConnectionStates(state)

        if (transitioning.isNotEmpty()) {
            postSystemMessageAndReconnect(state.toSystemMessageType(), transitioning)
        }
    }

    private fun handleClearChat(msg: IrcMessage) {
        val parsed =
            runCatching {
                ModerationMessage.parseClearChat(msg)
            }.getOrElse { return }

        chatMessageRepository.applyModerationMessage(parsed)
    }

    private fun handleClearMsg(msg: IrcMessage) {
        val parsed =
            runCatching {
                ModerationMessage.parseClearMessage(msg)
            }.getOrElse { return }

        chatMessageRepository.applyModerationMessage(parsed)
    }

    private suspend fun handleWhisper(ircMessage: IrcMessage) {
        val userId = ircMessage.tags["user-id"]?.toUserId()
        if (messageProcessor.isUserBlocked(userId)) {
            return
        }

        val userState = userStateRepository.userState.value
        val recipient = userState.displayName ?: return
        val message =
            runCatching {
                messageProcessor.processWhisper(
                    WhisperMessage.parseFromIrc(ircMessage, recipient, userState.color),
                ) as? WhisperMessage
            }.getOrNull() ?: return

        val userForSuggestion = message.name.valueOrDisplayName(message.displayName).toDisplayName()
        usersRepository.updateGlobalUser(message.name.lowercase(), userForSuggestion)
        val color = message.color
        if (color != null) {
            usersRepository.cacheUserColor(message.name, color)
        }

        val item = ChatItem(message, isMentionTab = true)
        _lastReceivedWhisperUser.value = message.name
        chatNotificationRepository.addWhisper(item)
        chatNotificationRepository.incrementMentionCount(WhisperMessage.WHISPER_CHANNEL, 1)
        chatNotificationRepository.emitMessages(listOf(item))
    }

    private suspend fun handleMessage(ircMessage: IrcMessage) {
        if (ircMessage.command == "NOTICE") {
            val msgId = ircMessage.tags["msg-id"]
            if (msgId in NoticeMessage.ROOM_STATE_CHANGE_MSG_IDS) {
                val channel = ircMessage.params[0].substring(1).toUserName()
                if (chatConnector.connectedAndHasModerateTopic(channel)) {
                    return
                }
            }
            if (msgId in AUTOMOD_NOTICE_MSG_IDS && chatConnector.connectedAndHasUserMessageTopic) {
                return
            }
        }

        if (messageProcessor.isUserBlocked(ircMessage.tags["user-id"]?.toUserId())) {
            return
        }

        val resolvedReward = resolveReward(ircMessage)
        val additionalMessages = resolvedReward?.toStandaloneMessage().orEmpty()

        val message =
            runCatchingCancellable {
                messageProcessor.processIrcMessage(ircMessage) { channel, id ->
                    chatMessageRepository.findMessage(id, channel, chatNotificationRepository.whispers)
                }
            }.getOrElse {
                logger.error(it) { "Failed to parse message" }
                return
            }?.let { resolveAutomaticRewardCost(it) }
                ?.let { attachRewardInfo(it, resolvedReward) } ?: return

        if (message is NoticeMessage && usersRepository.isGlobalChannel(message.channel)) {
            chatMessageRepository.broadcastToAllChannels(ChatItem(message, importance = ChatImportance.SYSTEM))
            return
        }

        trackUserState(message)

        val items =
            buildList {
                if (message is UserNoticeMessage && message.childMessage != null) {
                    add(ChatItem(message.childMessage))
                }
                val importance =
                    when (message) {
                        is NoticeMessage -> ChatImportance.SYSTEM
                        else -> ChatImportance.REGULAR
                    }
                add(ChatItem(message, importance = importance))
            }

        val channel =
            when (message) {
                is PrivMessage -> message.channel
                is UserNoticeMessage -> message.channel
                is NoticeMessage -> message.channel
                else -> return
            }

        chatMessageRepository.addMessages(channel, additionalMessages + items)
        chatNotificationRepository.emitMessages(items)

        val mentions =
            items
                .filter { it.message.highlights.hasMention() }
                .toMentionTabItems()

        if (mentions.isNotEmpty()) {
            chatNotificationRepository.addMentions(mentions)
        }

        if (channel != chatChannelProvider.activeChannel.value) {
            if (mentions.isNotEmpty()) {
                chatNotificationRepository.incrementMentionCount(channel, mentions.size)
            }

            if (message is PrivMessage) {
                chatNotificationRepository.setUnreadIfInactive(channel)
            }
        }
    }

    private suspend fun resolveReward(ircMessage: IrcMessage): PubSubMessage.PointRedemption? {
        val rewardId = ircMessage.tags["custom-reward-id"]?.takeIf { it.isNotEmpty() } ?: return null
        if (knownAutomodHeldIds.remove(rewardId)) {
            return null
        }

        return rewardMutex.withLock {
            knownRewards[rewardId]
                ?.also {
                    logger.debug { "Removing known reward $rewardId" }
                    knownRewards.remove(rewardId)
                }
                ?: run {
                    // Waiting is pointless without a connection and would stall the message pipeline
                    when {
                        !chatConnector.pubSubConnected -> null

                        else -> {
                            logger.debug { "Waiting for pubsub reward message with id $rewardId" }
                            withTimeoutOrNull(PUBSUB_TIMEOUT) {
                                chatConnector.pubSubEvents
                                    .filterIsInstance<PubSubMessage.PointRedemption>()
                                    .first { it.data.reward.id == rewardId }
                            }?.also { knownRewards[rewardId] = it }
                        }
                    }
                }
        }
    }

    private suspend fun PubSubMessage.PointRedemption.toStandaloneMessage(): List<ChatItem> {
        if (data.reward.requiresUserInput) return emptyList()
        val processed = messageProcessor.processReward(PointRedemptionMessage.parsePointReward(timestamp, data))
        return listOfNotNull(processed?.let(::ChatItem))
    }

    private fun attachRewardInfo(
        message: Message,
        reward: PubSubMessage.PointRedemption?,
    ): Message {
        if (message !is PrivMessage || reward == null) return message
        if (!reward.data.reward.requiresUserInput) return message
        val rewardData = reward.data.reward
        return message.copy(
            rewardCost = rewardData.effectiveCost,
            rewardTitle = rewardData.effectiveTitle,
            rewardImageUrl = rewardData.images?.imageLarge ?: rewardData.defaultImages?.imageLarge,
        )
    }

    private suspend fun resolveAutomaticRewardCost(message: Message): Message {
        if (message !is PrivMessage) return message
        val msgId = message.tags["msg-id"] ?: return message
        if (msgId != "gigantified-emote-message" && msgId != "animated-message") return message

        val reward = rewardMutex.withLock {
            knownRewards.remove(msgId)
        } ?: when {
            // Waiting is pointless without a connection and would stall the message pipeline
            !chatConnector.pubSubConnected -> null

            else ->
                withTimeoutOrNull(PUBSUB_TIMEOUT) {
                    chatConnector.pubSubEvents
                        .filterIsInstance<PubSubMessage.PointRedemption>()
                        .first { it.data.reward.effectiveId == msgId }
                }
        }

        val rewardData = reward?.data?.reward ?: return message
        return message.copy(
            rewardCost = rewardData.effectiveCost,
            rewardImageUrl = rewardData.images?.imageLarge ?: rewardData.defaultImages?.imageLarge,
        )
    }

    private fun trackUserState(message: Message) {
        if (message !is PrivMessage) {
            return
        }

        val color = message.color
        if (color != null) {
            usersRepository.cacheUserColor(message.name, color)
        }

        if (message.name == authDataStore.userName) {
            val previousLastMessage = getLastMessage(message.channel).orEmpty()
            val lastMessageWasCommand = previousLastMessage.startsWith('.') || previousLastMessage.startsWith('/')
            if (!lastMessageWasCommand && previousLastMessage.withoutInvisibleChar != message.originalMessage.withoutInvisibleChar) {
                setLastMessage(channel = message.channel, sent = message.originalMessage, typed = message.originalMessage.withoutInvisibleChar)
            }

            val hasVip = message.badges.any { badge -> badge.badgeTag?.startsWith("vip") == true }
            when {
                hasVip -> userStateRepository.addVipChannel(message.channel)
                else -> userStateRepository.removeVipChannel(message.channel)
            }
        }

        val userForSuggestion = message.name.valueOrDisplayName(message.displayName).toDisplayName()
        usersRepository.updateUser(message.channel, message.name.lowercase(), userForSuggestion)
    }

    private fun postEventSubDebugMessage(message: String) {
        val channels = chatChannelProvider.channels.value.orEmpty()
        val chatItem = SystemMessageType.Debug(message).toDebugChatItem()
        channels.forEach { channel ->
            chatMessageRepository.addMessages(channel, listOf(chatItem))
        }
    }

    private fun postSystemMessageAndReconnect(
        type: SystemMessageType,
        channels: Set<UserName> =
            chatChannelProvider.channels.value
                .orEmpty()
                .toSet(),
    ) {
        val reconnectedChannels = chatMessageRepository.addSystemMessageToChannels(type, channels)
        reconnectedChannels.forEach { channel ->
            scope.launch {
                if (chatSettingsDataStore.settings.first().loadMessageHistoryOnReconnect) {
                    loadRecentMessages(channel, isReconnect = true)
                }
            }
            // PubSub doesn't replay missed pin events, so re-sync after reconnects
            scope.launch { pinnedMessageRepository.fetch(channel) }
        }
    }

    private fun ConnectionState.toSystemMessageType(): SystemMessageType = when (this) {
        ConnectionState.DISCONNECTED -> SystemMessageType.Disconnected

        ConnectionState.CONNECTED,
        ConnectionState.CONNECTED_NOT_LOGGED_IN,
        -> SystemMessageType.Connected
    }

    private fun formatAutomodReason(
        reason: String,
        automod: AutomodReasonDto?,
        blockedTerm: BlockedTermReasonDto?,
        messageText: String,
    ): TextResource = when {
        reason == "automod" && automod != null -> {
            TextResource.Res(R.string.automod_reason_category, persistentListOf(automod.category, automod.level))
        }

        reason == "blocked_term" && blockedTerm != null -> {
            val terms =
                blockedTerm.termsFound
                    // boundaries are indexed in code points and occasionally out of range, drop terms that can't be sliced
                    .mapNotNull { found -> messageText.codePointSlice(found.boundary.startPos, found.boundary.endPos + 1) }
                    .joinToString { term -> "\"$term\"" }
            val count = blockedTerm.termsFound.size
            TextResource.PluralRes(R.plurals.automod_reason_blocked_terms, count, persistentListOf(count, terms))
        }

        else -> {
            TextResource.Plain(reason)
        }
    }

    companion object {
        private const val PUBSUB_TIMEOUT = 3000L
        private val AUTOMOD_NOTICE_MSG_IDS = setOf("msg_rejected", "msg_rejected_mandatory")
    }
}
