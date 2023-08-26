package com.flxrs.dankchat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.api.auth.AuthApiClient
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DankChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val authApiClient: AuthApiClient,
    private val dataRepository: DataRepository,
) : ViewModel() {

    val serviceEvents = dataRepository.serviceEvents
    private var started = false

    private val _validationResult = Channel<ValidationResult>(Channel.BUFFERED)
    val validationResult get() = _validationResult.receiveAsFlow()

    fun init(tryReconnect: Boolean) {
        if (tryReconnect && started) {
            chatRepository.reconnectIfNecessary()
            dataRepository.reconnectIfNecessary()
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
        val token = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return
        val result = authApiClient.validateUser(token)
            .fold(
                onSuccess = { result ->
                    dankChatPreferenceStore.userName = result.login
                    when {
                        authApiClient.validateScopes(result.scopes) -> ValidationResult.User(result.login)
                        else                                        -> ValidationResult.IncompleteScopes(result.login)
                    }
                },
                onFailure = { it.handleValidationError() }
            )
        _validationResult.send(result)
    }

    private fun Throwable.handleValidationError() = when {
        this is ApiException && status == HttpStatusCode.Unauthorized -> {
            dankChatPreferenceStore.clearLogin()
            ValidationResult.TokenInvalid
        }

        else                                                          -> {
            Log.e(TAG, "Failed to validate token: $message")
            ValidationResult.Failure
        }
    }

    companion object {
        private val TAG = DankChatViewModel::class.java.simpleName
    }
}
