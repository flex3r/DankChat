package com.flxrs.dankchat


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.ChatRepository
import com.flxrs.dankchat.data.DataRepository
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthSuffix
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _oauthResult = Channel<ValidationResult>(Channel.BUFFERED)
    val oAuthResult get() = _oauthResult.receiveAsFlow()

    fun init(name: String, oAuth: String, channels: List<String>, tryReconnect: Boolean) {
        if (tryReconnect && started) {
            chatRepository.reconnectIfNecessary()
        } else {
            started = true
            viewModelScope.launch {
                val nameToUse = validateUser(oAuth.withoutOAuthSuffix) ?: name
                dankChatPreferenceStore.userName = nameToUse
                chatRepository.connectAndJoin(nameToUse, oAuth, channels)
            }
        }
    }

    // validate name, NOTE: returning `null` to allow fallback, returning "" to not allow fallback
    private suspend fun validateUser(token: String): String? {
        if (!dankChatPreferenceStore.isLoggedIn) {
            return ""
        }
        return runCatching {
            val result = apiManager.validateUser(token)
            if (result == null) {
                _oauthResult.send(ValidationResult.TokenInvalid)
                return@runCatching null
            }
            // show Logging in as <user> only when success
            _oauthResult.send(ValidationResult.User(result.login))
            result.login
        }.getOrElse {
            // Connection failure
            _oauthResult.send(ValidationResult.Failure)
            null
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
    }
}
