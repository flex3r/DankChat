package com.flxrs.dankchat

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.service.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DankChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    var started = false
        private set

    fun init(name: String, oAuth: String, channels: List<String>, tryReconnect: Boolean) {
        if (tryReconnect && started) {
            chatRepository.reconnect(onlyIfNecessary = true)
        } else {
            started = true
            chatRepository.connectAndJoin(name, oAuth, channels)
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
    }
}