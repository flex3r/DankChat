package com.flxrs.dankchat.ui.main.input

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.chat.ChatChannelProvider
import com.flxrs.dankchat.data.repo.chat.SendWaitRepository
import com.flxrs.dankchat.utils.DateTimeUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SendWaitTimerViewModel(
    chatChannelProvider: ChatChannelProvider,
    sendWaitRepository: SendWaitRepository,
) : ViewModel() {
    val remainingTime: StateFlow<String?> =
        chatChannelProvider.activeChannel
            .flatMapLatest { channel ->
                when (channel) {
                    null -> flowOf(null)
                    else -> sendWaitRepository.getRemainingSeconds(channel)
                }
            }.map { seconds -> seconds?.let(DateTimeUtils::formatSeconds) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
}
