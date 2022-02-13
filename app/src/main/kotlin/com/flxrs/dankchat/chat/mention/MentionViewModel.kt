package com.flxrs.dankchat.chat.mention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MentionViewModel @Inject constructor(chatRepository: ChatRepository) : ViewModel() {
    val mentions: StateFlow<List<ChatItem>> = chatRepository.mentions
    val whispers: StateFlow<List<ChatItem>> = chatRepository.whispers

    val hasMentions: StateFlow<Boolean> = chatRepository.hasMentions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000), false)
    val hasWhispers: StateFlow<Boolean> = chatRepository.hasWhispers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000), false)

    companion object {
        private val TAG = MentionViewModel::class.java.simpleName
    }

}