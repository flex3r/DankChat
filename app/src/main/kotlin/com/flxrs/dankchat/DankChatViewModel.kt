package com.flxrs.dankchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.ChatRepository
import com.flxrs.dankchat.data.DataRepository
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthSuffix
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
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

    private val _validationError = MutableSharedFlow<OAuthValidationError>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val validationError get() = _validationError.asSharedFlow()

    fun init(name: String, oAuth: String, channels: List<String>, tryReconnect: Boolean) {
        if (tryReconnect && started) {
            chatRepository.reconnectIfNecessary()
        } else {
            started = true
            viewModelScope.launch {
                val token = oAuth.withoutOAuthSuffix

                var finished = false
                while (!finished) {
                    runCatching {
                        apiManager.validateUser(token)
                    }.fold({ result ->
                        val nameToUpdate = result?.login ?: name // fallback to old name if oAuth fail
                        _currentUserName.value = nameToUpdate
                        dankChatPreferenceStore.userName = nameToUpdate
                        chatRepository.connectAndJoin(nameToUpdate, oAuth, channels)

                        // oAuth failed (invalid or non existent token)
                        if (result == null) {
                            // invalid token
                            if (token.isNotEmpty()) {
                                _validationError.emit(OAuthValidationError.OAuthTokenInvalid)
                            }
                        }
                        finished = true
                    }) {
                        // Connection failure
                        _validationError.emit(OAuthValidationError.OAuthValidationFailure)
                        delay(5000)
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
    }
}