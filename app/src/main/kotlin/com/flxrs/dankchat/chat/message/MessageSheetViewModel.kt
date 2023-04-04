package com.flxrs.dankchat.chat.message

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RepliesRepository
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.command.CommandRepository
import com.flxrs.dankchat.data.repo.command.CommandResult
import com.flxrs.dankchat.utils.extensions.firstValueOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
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

    val state = chatRepository.userStateFlow.map { userState ->
        MessageSheetState(
            canModerate = args.canModerate && args.channel != null && args.channel in userState.moderationChannels,
            hasReplyThread = args.canReply && args.replyMessageId != null && repliesRepository.hasMessageThread(args.replyMessageId),
            canReply = args.canReply
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), MessageSheetState(canModerate = false, hasReplyThread = false, canReply = args.canReply))

    suspend fun timeoutUser(index: Int) {
        val duration = TIMEOUT_MAP[index] ?: return
        sendCommand(".timeout ${args.name} $duration")
    }

    suspend fun banUser() {
        sendCommand(".ban ${args.name}")
    }

    suspend fun unbanUser() {
        sendCommand(".unban ${args.name}")
    }

    suspend fun deleteMessage() {
        val messageId = args.messageId
        sendCommand(".delete $messageId")
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
