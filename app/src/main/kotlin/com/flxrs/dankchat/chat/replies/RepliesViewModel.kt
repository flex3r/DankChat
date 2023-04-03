package com.flxrs.dankchat.chat.replies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.RepliesRepository
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class RepliesViewModel @Inject constructor(
    chatRepository: ChatRepository,
    repliesRepository: RepliesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val args = RepliesFragmentArgs.fromSavedStateHandle(savedStateHandle)

    val items = repliesRepository.getThreadItemsFlow(args.rootMessageId) {
        args.channel?.let {
            chatRepository.findMessage(it, args.rootMessageId)?.message
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), emptyList())
}
