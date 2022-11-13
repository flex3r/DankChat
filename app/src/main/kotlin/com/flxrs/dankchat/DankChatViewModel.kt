package com.flxrs.dankchat


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.api.dto.ValidateResultDto
import com.flxrs.dankchat.data.repo.ChatRepository
import com.flxrs.dankchat.data.repo.DataRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthSuffix
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.*
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

    private val _validationResult = Channel<ValidationResult>(Channel.BUFFERED)
    val validationResult get() = _validationResult.receiveAsFlow()

    fun init(tryReconnect: Boolean) {
        if (tryReconnect && started) {
            chatRepository.reconnectIfNecessary()
        } else {
            started = true

            viewModelScope.launch {
                if (dankChatPreferenceStore.isLoggedIn) {
                    validateUser()
                }

                chatRepository.connectAndJoin()
            }
        }
    }

    private suspend fun validateUser() {
        // no token = nothing to validate 4head
        val token = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return

        runCatching {
            when (val result = apiManager.validateUser(token)) {
                is ValidateResultDto.Error     -> result.handleValidationError()
                is ValidateResultDto.ValidUser -> {
                    _validationResult.send(ValidationResult.User(result.login))
                    dankChatPreferenceStore.userName = result.login
                }
            }
        }.getOrElse {
            Log.e(TAG, "Failed to validate token:", it)
            _validationResult.send(ValidationResult.Failure)
        }
    }

    private suspend fun ValidateResultDto.Error.handleValidationError() = when (statusCode) {
        HttpStatusCode.Unauthorized -> {
            dankChatPreferenceStore.clearLogin()
            _validationResult.send(ValidationResult.TokenInvalid)
        }

        else                        -> {
            Log.e(TAG, "Failed to validate token: $message")
            _validationResult.send(ValidationResult.Failure)
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
    }
}
