package com.flxrs.dankchat.chat

import android.util.Log
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.flxrs.dankchat.service.ChatRepository
import kotlinx.coroutines.CoroutineExceptionHandler

class ChatViewModel @ViewModelInject constructor(@Assisted savedStateHandle: SavedStateHandle, repository: ChatRepository) : ViewModel() {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
    }
    private val channel = savedStateHandle.get<String>(ChatFragment.CHANNEL_ARG) ?: ""
    val chat: LiveData<List<ChatItem>> = repository.getChat(channel).asLiveData(coroutineExceptionHandler)

    companion object {
        private val TAG = ChatViewModel::class.java.simpleName
    }
}