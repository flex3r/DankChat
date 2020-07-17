package com.flxrs.dankchat.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.flxrs.dankchat.service.ChatRepository
import kotlinx.coroutines.CoroutineExceptionHandler

class ChatViewModel(repository: ChatRepository, channel: String) : ViewModel() {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
    }
    val chat: LiveData<List<ChatItem>> = repository.getChat(channel).asLiveData(coroutineExceptionHandler)

    companion object {
        private val TAG = ChatViewModel::class.java.simpleName
    }
}