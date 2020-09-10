package com.flxrs.dankchat.chat.mention

import android.util.Log
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.flxrs.dankchat.chat.ChatItem
import com.flxrs.dankchat.service.ChatRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.mapLatest

class MentionViewModel @ViewModelInject constructor(@Assisted savedStateHandle: SavedStateHandle, repository: ChatRepository) : ViewModel() {
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, Log.getStackTraceString(t))
    }
    val mentions: LiveData<List<ChatItem>> = repository.mentions.mapLatest { list ->
        list.map { it.apply { isMentionTab = true } }
    }.asLiveData(coroutineExceptionHandler)

    val whispers: LiveData<List<ChatItem>> = repository.whispers.mapLatest { list ->
        list.map { it.apply { isMentionTab = true } }
    }.asLiveData(coroutineExceptionHandler)

    companion object {
        private val TAG = MentionViewModel::class.java.simpleName
    }

}