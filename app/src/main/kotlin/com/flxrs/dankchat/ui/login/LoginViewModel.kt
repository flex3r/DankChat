package com.flxrs.dankchat.ui.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.auth.AuthApiClient
import com.flxrs.dankchat.data.api.auth.dto.ValidateDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

private val logger = KotlinLogging.logger("LoginViewModel")

@KoinViewModel
class LoginViewModel(
    private val authApiClient: AuthApiClient,
    private val authDataStore: AuthDataStore,
) : ViewModel() {
    @Immutable
    sealed interface LoginEvent {
        data object Success : LoginEvent

        data object TokenMissing : LoginEvent

        data object ValidationFailed : LoginEvent
    }

    private val eventChannel = Channel<LoginEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    val loginUrl = AuthApiClient.LOGIN_URL

    fun isRedirectUrl(url: String): Boolean = url.startsWith(AuthApiClient.REDIRECT_URL)

    fun parseToken(fragment: String) = viewModelScope.launch {
        if (!fragment.startsWith("access_token")) {
            eventChannel.send(LoginEvent.TokenMissing)
            return@launch
        }

        val token =
            fragment
                .substringAfter("access_token=")
                .substringBefore("&scope=")

        val result =
            authApiClient.validateUser(token).fold(
                onSuccess = { saveLoginDetails(token, it) },
                onFailure = {
                    logger.error { "Failed to validate token: ${it.message}" }
                    LoginEvent.ValidationFailed
                },
            )
        eventChannel.send(result)
    }

    private suspend fun saveLoginDetails(
        oAuth: String,
        validateDto: ValidateDto,
    ): LoginEvent {
        authDataStore.login(
            oAuthKey = oAuth,
            userName = validateDto.login.value.lowercase(),
            userId = validateDto.userId.value,
            clientId = validateDto.clientId,
        )
        return LoginEvent.Success
    }
}
