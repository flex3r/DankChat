package com.flxrs.dankchat.chat.mention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.data.repo.chat.ChatRepository

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.stateIn
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class MentionViewModel(chatRepository: ChatRepository) : ViewModel() {
    val mentions: StateFlow<List<ChatItem>> = chatRepository.mentions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), emptyList())
    val whispers: StateFlow<List<ChatItem>> = chatRepository.whispers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), emptyList())

    val hasMentions: StateFlow<Boolean> = chatRepository.hasMentions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)
    val hasWhispers: StateFlow<Boolean> = chatRepository.hasWhispers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeout = 5.seconds), false)
}
