package com.flxrs.dankchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.ChatRepository
import com.flxrs.dankchat.data.DataRepository
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DankChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val apiManager: ApiManager,
    dataRepository: DataRepository,
) : ViewModel() {

    val commands = dataRepository.commands
    var started = false
        private set

    private val _currentUserName = MutableStateFlow<String?>(null)
    val currentUserName: StateFlow<String?> get() = _currentUserName.asStateFlow()

    fun init(name: String, oAuth: String, channels: List<String>, tryReconnect: Boolean) {
        if (tryReconnect && started) {
            chatRepository.reconnectIfNecessary()
        } else {
            started = true
            viewModelScope.launch {
                val token = oAuth.substring(6) // remove the "oauth:" prefix
                val result = apiManager.validateUser(token)
                val nameToUpdate = result?.login ?: name // fallback to old name if oAuth fail
                _currentUserName.value = nameToUpdate
                dankChatPreferenceStore.userName = nameToUpdate
                chatRepository.connectAndJoin(nameToUpdate, oAuth, channels)
            }
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
    }
}