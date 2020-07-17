package com.flxrs.dankchat.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.service.TwitchRepository

class ChatViewModel(repository: TwitchRepository, channel: String) : ViewModel() {
    val chat: LiveData<List<ChatItem>> = repository.getChat(channel)
}