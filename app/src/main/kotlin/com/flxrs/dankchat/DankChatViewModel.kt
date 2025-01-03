package com.flxrs.dankchat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.api.auth.AuthApiClient
import com.flxrs.dankchat.data.repo.chat.ChatRepository
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.seconds

@KoinViewModel
class DankChatViewModel(
    private val chatRepository: ChatRepository,
    private val dankChatPreferenceStore: DankChatPreferenceStore,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
    private val authApiClient: AuthApiClient,
    private val dataRepository: DataRepository,
) : ViewModel() {

    val serviceEvents = dataRepository.serviceEvents
    private var started = false

    private val _validationResult = Channel<ValidationResult>(Channel.BUFFERED)
    val validationResult get() = _validationResult.receiveAsFlow()

    val isTrueDarkModeEnabled get() = appearanceSettingsDataStore.current().trueDarkTheme
    val keepScreenOn = appearanceSettingsDataStore.settings
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), appearanceSettingsDataStore.current().keepScreenOn)

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

    fun checkLogin() {
        if (dankChatPreferenceStore.isLoggedIn && dankChatPreferenceStore.oAuthKey.isNullOrBlank()) {
            dankChatPreferenceStore.clearLogin()
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
