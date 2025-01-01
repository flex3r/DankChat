package com.flxrs.dankchat.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.stateIn
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class ChatViewModel(savedStateHandle: SavedStateHandle, repository: ChatRepository) : ViewModel() {
    private val args = ChatFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val chat: StateFlow<List<ChatItem>> = repository
        .getChat(args.channel)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), emptyList())

    companion object {
        private val TAG = ChatViewModel::class.java.simpleName
    }
}
