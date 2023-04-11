package com.flxrs.dankchat.chat.message

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RepliesRepository
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.command.CommandRepository
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.message.PrivMessage
import com.flxrs.dankchat.data.twitch.message.WhisperMessage
import com.flxrs.dankchat.utils.extensions.firstValueOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class MessageSheetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repliesRepository: RepliesRepository,
    private val chatRepository: ChatRepository,
    private val commandRepository: CommandRepository,
) : ViewModel() {

    private val args = MessageSheetFragmentArgs.fromSavedStateHandle(savedStateHandle)

    private val message = flowOf(chatRepository.findMessage(args.messageId, args.channel))
    private val connectionState = chatRepository.getConnectionState(args.channel ?: WhisperMessage.WHISPER_CHANNEL)

    val state = combine(chatRepository.userStateFlow, connectionState, message) { userState, connectionState, message ->
        when (message) {
            null -> MessageSheetState.NotFound
            else -> {
                val asPrivMessage = message as? PrivMessage
                val asWhisperMessage = message as? WhisperMessage
                val replyMessageId = asPrivMessage?.thread?.id
                val name = asPrivMessage?.name ?: asWhisperMessage?.name ?: return@combine MessageSheetState.NotFound
                val replyName = asPrivMessage?.thread?.name ?: name
                val originalMessage = asPrivMessage?.originalMessage ?: asWhisperMessage?.originalMessage
                MessageSheetState.Found(
                    messageId = message.id,
                    replyMessageId = replyMessageId ?: message.id,
                    replyName = replyName,
                    name = name,
                    originalMessage = originalMessage.orEmpty(),
                    canModerate = args.canModerate && args.channel != null && args.channel in userState.moderationChannels,
                    hasReplyThread = args.canReply && replyMessageId != null && repliesRepository.hasMessageThread(replyMessageId),
                    canReply = connectionState == ConnectionState.CONNECTED && args.canReply
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), MessageSheetState.Default)

    suspend fun timeoutUser(index: Int) {
        val duration = TIMEOUT_MAP[index] ?: return
        val name = (state.value as? MessageSheetState.Found)?.name ?: return
        sendCommand(".timeout $name $duration")
    }

    suspend fun banUser() {
        val name = (state.value as? MessageSheetState.Found)?.name ?: return
        sendCommand(".ban $name")
    }

    suspend fun unbanUser() {
        val name = (state.value as? MessageSheetState.Found)?.name ?: return
        sendCommand(".unban $name")
    }

    suspend fun deleteMessage() {
        sendCommand(".delete ${args.messageId}")
    }

    private suspend fun sendCommand(message: String) {
        val channel = args.channel ?: return
        val roomState = chatRepository.getRoomState(channel).firstValueOrNull ?: return
        val userState = chatRepository.userStateFlow.value
        val result = runCatching {
            commandRepository.checkForCommands(message, channel, roomState, userState)
        }.getOrNull() ?: return

        when (result) {
            is CommandResult.IrcCommand            -> chatRepository.sendMessage(message)
            is CommandResult.AcceptedTwitchCommand -> result.response?.let { chatRepository.makeAndPostCustomSystemMessage(it, channel) }
            else                                   -> Log.d(TAG, "Unhandled command result: $result")
        }
    }

    companion object {
        private val TAG = MessageSheetViewModel::class.java.simpleName
        private val TIMEOUT_MAP = mapOf(
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
    }
}
