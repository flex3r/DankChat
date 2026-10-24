package com.flxrs.dankchat.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.data.chat.ChatItem
import com.flxrs.dankchat.data.repo.PinnedMessage
import com.flxrs.dankchat.data.repo.PinnedMessageRepository
import com.flxrs.dankchat.data.repo.PinnedMessageState
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.data.repo.chat.MessageProcessor
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.repo.chat.UsersRepository
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.utils.DateTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Immutable
sealed interface PinnedMessageUiState {
    data object Hidden : PinnedMessageUiState

    data object Collapsed : PinnedMessageUiState

    data class Expanded(
        val pinnedBy: DisplayName,
        val message: ChatMessageUiState.PrivMessageUi,
        val remainingTime: String?,
        val canModerate: Boolean,
    ) : PinnedMessageUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class PinnedMessageViewModel(
    @InjectedParam private val channel: UserName,
    private val pinnedMessageRepository: PinnedMessageRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatNotificationRepository: ChatNotificationRepository,
    private val messageProcessor: MessageProcessor,
    private val chatMessageMapper: ChatMessageMapper,
    private val preferenceStore: DankChatPreferenceStore,
    private val chatSettingsDataStore: ChatSettingsDataStore,
    private val usersRepository: UsersRepository,
    userStateRepository: UserStateRepository,
) : ViewModel() {
    private val expanded = MutableStateFlow(false)
    private val autoCollapsePending = MutableStateFlow(false)
    private val isVisible = MutableStateFlow(false)
    private val alwaysShow =
        chatSettingsDataStore.settings
            .map { it.alwaysShowPinnedMessage }
            .distinctUntilChanged()
    private var cachedMessageUi: Pair<String, ChatMessageUiState.PrivMessageUi>? = null

    init {
        viewModelScope.launch {
            var lastPinKey: Pair<String, Instant?>? = null
            pinnedMessageRepository.getState(channel).collect { state ->
                when (state) {
                    is PinnedMessageState.Pinned -> {
                        val pinKey = state.message.messageId to state.message.endsAt
                        if (pinKey != lastPinKey) {
                            lastPinKey = pinKey
                            expand()
                        }
                    }

                    else -> {
                        lastPinKey = null
                        autoCollapsePending.value = false
                        expanded.value = false
                    }
                }
            }
        }

        // The countdown only runs while the pin is on screen and restarts when it comes back into view
        viewModelScope.launch {
            combine(expanded, autoCollapsePending, isVisible, alwaysShow) { isExpanded, isPending, visible, always ->
                isExpanded && isPending && visible && !always
            }.distinctUntilChanged()
                .collectLatest { shouldCollapse ->
                    if (shouldCollapse) {
                        delay(AUTO_COLLAPSE_DELAY)
                        collapse()
                    }
                }
        }

        viewModelScope.launch {
            alwaysShow.drop(1).filter { it }.collect {
                if (pinnedMessageRepository.getState(channel).value is PinnedMessageState.Pinned) {
                    expand()
                }
            }
        }
    }

    val uiState: StateFlow<PinnedMessageUiState> =
        combine(
            pinnedMessageRepository.getState(channel),
            userStateRepository.userState.map { channel in it.moderationChannels }.distinctUntilChanged(),
            expanded,
        ) { state, canModerate, isExpanded ->
            Triple(state, canModerate, isExpanded)
        }.transformLatest { (state, canModerate, isExpanded) ->
            when {
                state !is PinnedMessageState.Pinned -> emit(PinnedMessageUiState.Hidden)
                !isExpanded -> emit(PinnedMessageUiState.Collapsed)
                else -> emitExpanded(state.message, canModerate)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 500L), PinnedMessageUiState.Hidden)

    fun toggleExpanded() {
        autoCollapsePending.value = false
        expanded.value = !expanded.value
    }

    fun collapse() {
        autoCollapsePending.value = false
        expanded.value = false
    }

    fun setVisible(visible: Boolean) {
        isVisible.value = visible
    }

    fun unpin() {
        viewModelScope.launch {
            pinnedMessageRepository.unpin(channel).onFailure { error ->
                val statusCode = (error as? HelixApiException)?.status?.value
                chatMessageRepository.addSystemMessage(channel, SystemMessageType.PinnedMessageActionFailed(statusCode = statusCode, pin = false))
            }
        }
    }

    private fun expand() {
        autoCollapsePending.value = true
        expanded.value = true
    }

    private suspend fun FlowCollector<PinnedMessageUiState>.emitExpanded(
        pin: PinnedMessage,
        canModerate: Boolean,
    ) {
        val messageUi = buildMessageUi(pin)
        if (messageUi == null) {
            emit(PinnedMessageUiState.Hidden)
            return
        }

        val endsAt = pin.endsAt
        when (endsAt) {
            null -> emit(PinnedMessageUiState.Expanded(pin.pinnedByName, messageUi, remainingTime = null, canModerate))

            else -> while (true) {
                val remainingSeconds = (endsAt - Clock.System.now()).inWholeSeconds.coerceAtLeast(0L)
                val remainingTime = DateTimeUtils.formatSeconds(remainingSeconds.toInt())
                emit(PinnedMessageUiState.Expanded(pin.pinnedByName, messageUi, remainingTime, canModerate))
                delay(1.seconds)
            }
        }
    }

    private suspend fun buildMessageUi(pin: PinnedMessage): ChatMessageUiState.PrivMessageUi? {
        cachedMessageUi?.let { (messageId, ui) ->
            if (messageId == pin.messageId) {
                return ui
            }
        }

        // Prefer the original message from the chat buffer, the synthetic fallback rebuilds
        // Twitch emotes from the Helix pin fragments and third-party emotes from the text
        val existing = chatMessageRepository.findMessage(pin.messageId, channel, chatNotificationRepository.whispers) as? PrivMessage
        val message = existing ?: messageProcessor.reparseEmotesAndBadges(pin.toSyntheticMessage()) as? PrivMessage ?: return null
        val messageUi =
            chatMessageMapper.mapToUiState(
                item = ChatItem(message),
                chatSettings = chatSettingsDataStore.current(),
                preferenceStore = preferenceStore,
                isAlternateBackground = false,
            ) as? ChatMessageUiState.PrivMessageUi ?: return null

        cachedMessageUi = pin.messageId to messageUi
        return messageUi
    }

    private fun PinnedMessage.toSyntheticMessage(): PrivMessage = PrivMessage(
        timestamp = startsAt.toEpochMilliseconds(),
        id = messageId,
        channel = channel,
        sourceChannel = null,
        userId = senderId,
        name = senderLogin,
        displayName = senderName,
        color = usersRepository.getCachedUserColor(senderLogin),
        message = text,
        tags = emptyMap(),
        emoteData = Message.EmoteData(
            message = text,
            channel = channel,
            emotesWithPositions = emotesWithPositions,
        ),
    )

    companion object {
        private val AUTO_COLLAPSE_DELAY = 30.seconds
    }
}
