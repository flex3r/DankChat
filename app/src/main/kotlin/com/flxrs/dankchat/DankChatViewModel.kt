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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _oauthResult = MutableSharedFlow<OAuthResult>(extraBufferCapacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val oAuthResult get() = _oauthResult.asSharedFlow()

    fun init(name: String, oAuth: String, channels: List<String>, tryReconnect: Boolean) {
        if (tryReconnect && started) {
            chatRepository.reconnectIfNecessary()
        } else {
            started = true
            viewModelScope.launch {
                val token = oAuth.withoutOAuthSuffix
                val nameToUse = runCatching {
                    val result = apiManager.validateUser(token)
                    if (result == null && token.isNotEmpty()) {
                        _oauthResult.emit(OAuthResult.OAuthTokenInvalid)
                    }
                    val nameWithFallback = result?.login ?: name
                    _oauthResult.emit(OAuthResult.UserName(nameWithFallback)) // show username, only if not connection error
                    nameWithFallback
                }.getOrElse {
                    // Connection failure
                    _oauthResult.emit(OAuthResult.OAuthValidationFailure)
                    name // fallback to old name
                }
                dankChatPreferenceStore.userName = nameToUse
                chatRepository.connectAndJoin(nameToUse, oAuth, channels)
            }
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
    }
}
