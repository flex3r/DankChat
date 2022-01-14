package com.flxrs.dankchat

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DankChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    dataRepository: DataRepository,
) : ViewModel() {

    val commands = dataRepository.commands
    var started = false
        private set

    fun init(name: String, oAuth: String, channels: List<String>, tryReconnect: Boolean) {
        if (tryReconnect && started) {
            chatRepository.reconnectIfNecessary()
        } else {
            started = true
            chatRepository.connectAndJoin(name, oAuth, channels)
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
    }
}