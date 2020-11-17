package com.flxrs.dankchat.chat.mention

import android.util.Log
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.ChatRepository
import kotlinx.coroutines.CoroutineExceptionHandler

class MentionViewModel @ViewModelInject constructor(chatRepository: ChatRepository) : ViewModel() {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
    }
    val mentions: LiveData<List<ChatItem>> = chatRepository.mentions.asLiveData(coroutineExceptionHandler)
    val whispers: LiveData<List<ChatItem>> = chatRepository.whispers.asLiveData(coroutineExceptionHandler)

    companion object {
        private val TAG = MentionViewModel::class.java.simpleName
    }

}