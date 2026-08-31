package com.flxrs.dankchat.ui.chat.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.helix.HelixApiException
import com.flxrs.dankchat.data.repo.PinnedMessageRepository
import com.flxrs.dankchat.data.repo.RepliesRepository
import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.repo.chat.ChatConnector
import com.flxrs.dankchat.data.repo.chat.ChatMessageRepository
import com.flxrs.dankchat.data.repo.chat.ChatNotificationRepository
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.chat.UserStateRepository
import com.flxrs.dankchat.data.repo.command.CommandRepository
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.message.AutomodMessage
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.SystemMessageType
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.ui.chat.messages.common.extractUrls
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

data class MessageOptionsUiState(
    val optionsState: MessageOptionsState,
    val params: MessageOptionsParams,
)

@KoinViewModel
class MessageOptionsViewModel(
    private val chatRepository: ChatRepository,
    private val channelRepository: ChannelRepository,
    private val userStateRepository: UserStateRepository,
    private val commandRepository: CommandRepository,
    private val repliesRepository: RepliesRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatConnector: ChatConnector,
    private val chatNotificationRepository: ChatNotificationRepository,
    private val pinnedMessageRepository: PinnedMessageRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<MessageOptionsUiState?>(null)
    val state: StateFlow<MessageOptionsUiState?> = _state.asStateFlow()
    val isActive = _state.map { it != null }

    private var currentParams: MessageOptionsParams? = null
    private var collectJob: Job? = null

    fun show(params: MessageOptionsParams) {
        currentParams = params
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            val messageFlow = flowOf(chatMessageRepository.findMessage(params.messageId, params.channel, chatNotificationRepository.whispers))
            val connectionStateFlow = chatConnector.getConnectionState(params.channel ?: WhisperMessage.WHISPER_CHANNEL)

            combine(
                userStateRepository.userState,
                connectionStateFlow,
                messageFlow,
            ) { userState, connectionState, message ->
                when (message) {
                    null -> {
                        MessageOptionsState.NotFound
                    }

                    is AutomodMessage -> {
                        val originalMessage = message.messageText.orEmpty()
                        MessageOptionsState.Found.AutomodMessage(
                            name = message.userName,
                            originalMessage = originalMessage,
                            canModerate = params.canModerate && params.channel != null && params.channel in userState.moderationChannels,
                            urls = extractUrls(originalMessage).toImmutableList(),
                        )
                    }

                    else -> {
                        val asPrivMessage = message as? PrivMessage
                        val asWhisperMessage = message as? WhisperMessage
                        val thread = asPrivMessage?.thread
                        val rootId = thread?.rootId
                        val name = asPrivMessage?.name ?: asWhisperMessage?.name ?: return@combine MessageOptionsState.NotFound
                        val originalMessage = (asPrivMessage?.originalMessage ?: asWhisperMessage?.originalMessage).orEmpty()
                        MessageOptionsState.Found.RegularMessage(
                            messageId = message.id,
                            rootThreadId = rootId ?: message.id,
                            rootThreadName = thread?.name,
                            rootThreadMessage = thread?.message,
                            replyName = name,
                            name = name,
                            originalMessage = originalMessage,
                            canModerate = params.canModerate && params.channel != null && params.channel in userState.moderationChannels,
                            urls = extractUrls(originalMessage).toImmutableList(),
                            hasReplyThread = params.canReply && rootId != null && repliesRepository.hasMessageThread(rootId),
                            canReply = connectionState == ConnectionState.CONNECTED && params.canReply,
                        )
                    }
                }
            }.collect { optionsState ->
                _state.value = MessageOptionsUiState(
                    optionsState = optionsState,
                    params = params,
                )
            }
        }
    }

    fun dismiss() {
        collectJob?.cancel()
        currentParams = null
        _state.value = null
    }

    fun timeoutUser(index: Int) = viewModelScope.launch {
        val duration = TIMEOUT_MAP[index] ?: return@launch
        val name = (_state.value?.optionsState as? MessageOptionsState.Found)?.name ?: return@launch
        sendCommand(".timeout $name $duration")
    }

    fun banUser() = viewModelScope.launch {
        val name = (_state.value?.optionsState as? MessageOptionsState.Found)?.name ?: return@launch
        sendCommand(".ban $name")
    }

    fun unbanUser() = viewModelScope.launch {
        val name = (_state.value?.optionsState as? MessageOptionsState.Found)?.name ?: return@launch
        sendCommand(".unban $name")
    }

    fun warnUser(reason: String) = viewModelScope.launch {
        val name = (_state.value?.optionsState as? MessageOptionsState.Found)?.name ?: return@launch
        sendCommand(".warn $name $reason")
    }

    fun deleteMessage() = viewModelScope.launch {
        val messageId = currentParams?.messageId ?: return@launch
        sendCommand(".delete $messageId")
    }

    fun pinMessage(index: Int) = viewModelScope.launch {
        val channel = currentParams?.channel ?: return@launch
        val messageId = currentParams?.messageId ?: return@launch
        val duration = PIN_DURATION_MAP[index]?.seconds
        pinnedMessageRepository
            .pin(channel, messageId, duration)
            .onFailure { error ->
                val statusCode = (error as? HelixApiException)?.status?.value
                chatMessageRepository.addSystemMessage(channel, SystemMessageType.PinnedMessageActionFailed(statusCode = statusCode, pin = true))
            }
    }

    private suspend fun sendCommand(message: String) {
        val activeChannel = currentParams?.channel ?: return
        val roomState = channelRepository.getRoomState(activeChannel) ?: return
        val userState = userStateRepository.userState.value
        val result =
            runCatching {
                commandRepository.checkForCommands(message, activeChannel, roomState, userState)
            }.getOrNull() ?: return

        when (result) {
            is CommandResult.IrcCommand -> chatRepository.sendMessage(message, forceIrc = true)
            is CommandResult.AcceptedTwitchCommand -> result.response?.let { chatRepository.makeAndPostCustomSystemMessage(it, activeChannel) }
            else -> Unit
        }
    }

    companion object {
        private val TIMEOUT_MAP =
            mapOf(
                0 to "1",
                1 to "30",
                2 to "60",
                3 to "300",
                4 to "600",
                5 to "1800",
                6 to "3600",
                7 to "86400",
                8 to "604800",
            )

        // Index 0 = no duration, the pin stays until the stream ends or it gets unpinned
        private val PIN_DURATION_MAP =
            mapOf<Int, Long?>(
                0 to null,
                1 to 60L,
                2 to 300L,
                3 to 600L,
                4 to 1200L,
                5 to 1800L,
            )
    }
}
