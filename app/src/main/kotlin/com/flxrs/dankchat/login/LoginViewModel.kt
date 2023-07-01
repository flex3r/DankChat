package com.flxrs.dankchat.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.auth.AuthApiClient
import com.flxrs.dankchat.data.api.auth.dto.ValidateDto
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApiClient: AuthApiClient,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    data class TokenParseEvent(val successful: Boolean)

    private val eventChannel = Channel<TokenParseEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val loginUrl = AuthApiClient.LOGIN_URL

    fun parseToken(fragment: String) = viewModelScope.launch {
        if (!fragment.startsWith("access_token")) {
            eventChannel.send(TokenParseEvent(successful = false))
            return@launch
        }

        val token = fragment
            .substringAfter("access_token=")
            .substringBefore("&scope=")

        val result = authApiClient.validateUser(token).fold(
            onSuccess = { saveLoginDetails(token, it) },
            onFailure = {
                Log.e(TAG, "Failed to validate token: ${it.message}")
                TokenParseEvent(successful = false)
            }
        )
        eventChannel.send(result)
    }

    private fun saveLoginDetails(oAuth: String, validateDto: ValidateDto): TokenParseEvent {
        dankChatPreferenceStore.apply {
            oAuthKey = "oauth:$oAuth"
            userName = validateDto.login.lowercase()
            userIdString = validateDto.userId
            clientId = validateDto.clientId
            isLoggedIn = true
        }

        return TokenParseEvent(successful = true)
    }

    companion object {
        private val TAG = LoginViewModel::class.java.simpleName
    }
}
