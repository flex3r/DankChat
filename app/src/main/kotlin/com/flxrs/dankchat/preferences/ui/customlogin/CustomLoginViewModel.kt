package com.flxrs.dankchat.preferences.ui.customlogin

import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.api.auth.AuthApiClient
import com.flxrs.dankchat.data.api.auth.dto.ValidateDto
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginState.Default
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginState.Failure
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginState.Loading
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginState.MissingScopes
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginState.TokenEmpty
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginState.TokenInvalid
import com.flxrs.dankchat.preferences.ui.customlogin.CustomLoginState.Validated
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Factory

@Factory
class CustomLoginViewModel(
    private val authApiClient: AuthApiClient,
    private val dankChatPreferenceStore: DankChatPreferenceStore
) {

    private val _customLoginState = MutableStateFlow<CustomLoginState>(Default)
    val customLoginState = _customLoginState.asStateFlow()

    suspend fun validateCustomLogin(oAuthToken: String) {
        if (oAuthToken.isBlank()) {
            _customLoginState.update { TokenEmpty }
            return
        }

        _customLoginState.update { Loading }

        val token = oAuthToken.withoutOAuthPrefix
        val result = authApiClient.validateUser(token).fold(
            onSuccess = { result ->
                val scopes = result.scopes.orEmpty()
                when {
                    !authApiClient.validateScopes(scopes) -> MissingScopes(
                        missingScopes = authApiClient.missingScopes(scopes).joinToString(),
                        validation = result,
                        token = token,
                        dialogOpen = true,
                    )

                    else                                  -> {
                        saveLogin(token, result)
                        Validated
                    }
                }
            },
            onFailure = {
                when {
                    it is ApiException && it.status == HttpStatusCode.Unauthorized -> TokenInvalid
                    else                                                           -> Failure(it.message.orEmpty())
                }
            }
        )

        _customLoginState.update { result }
    }

    fun dismissMissingScopesDialog() {
        _customLoginState.update { (it as? MissingScopes)?.copy(dialogOpen = false) ?: it }
    }

    fun saveLogin(token: String, validateDto: ValidateDto) = with(dankChatPreferenceStore) {
        clientId = validateDto.clientId
        oAuthKey = "oauth:$token"
        userIdString = validateDto.userId
        userName = validateDto.login
        isLoggedIn = true
    }

    fun getScopes() = AuthApiClient.SCOPES.joinToString(separator = "+")
    fun getToken() = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix.orEmpty()
}
