package com.flxrs.dankchat.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.service.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: ChatRepository) : ViewModel() {
    private val args = ChatFragmentArgs.fromSavedStateHandle(savedStateHandle)
    val chat: StateFlow<List<ChatItem>> = repository.getChat(args.channel)

    companion object {
        private val TAG = ChatViewModel::class.java.simpleName
    }
}