package com.flxrs.dankchat.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.ApiManager
import com.flxrs.dankchat.data.api.dto.ValidateUserDto
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val apiManager: ApiManager,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
) : ViewModel() {

    data class TokenParseEvent(val successful: Boolean)

    private val eventChannel = Channel<TokenParseEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun parseToken(fragment: String) = viewModelScope.launch {
        if (!fragment.startsWith("access_token")) {
            eventChannel.send(TokenParseEvent(successful = false))
            return@launch
        }

        val token = fragment
            .substringAfter("access_token=")
            .substringBefore("&scope=")
        val result = apiManager.validateUser(token)
        val successful = saveLoginDetails(token, result)
        eventChannel.send(TokenParseEvent(successful))
    }

    private fun saveLoginDetails(oAuth: String, validateDto: ValidateUserDto?): Boolean {
        return when {
            validateDto == null || validateDto.login.isBlank() -> false
            else                                               -> {
                dankChatPreferenceStore.apply {
                    oAuthKey = "oauth:$oAuth"
                    userName = validateDto.login.lowercase(Locale.getDefault())
                    userIdString = validateDto.userId
                    isLoggedIn = true
                }
                true
            }
        }
    }
}