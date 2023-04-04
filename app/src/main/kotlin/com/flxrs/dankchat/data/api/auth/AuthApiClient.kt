package com.flxrs.dankchat.data.api.auth

import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.api.auth.dto.ValidateDto
import com.flxrs.dankchat.data.api.auth.dto.ValidateErrorDto
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthApiClient @Inject constructor(private val authApi: AuthApi, private val json: Json) {

    suspend fun validateUser(token: String): Result<ValidateDto> = runCatching {
        val response = authApi.validateUser(token)
        when {
            response.status.isSuccess() -> response.body()
            else                        -> {
                val error = json.decodeOrNull<ValidateErrorDto>(response.bodyAsText())
                throw ApiException(status = response.status, response.request.url, error?.message)
            }
        }
    }

    fun validateScopes(scopes: List<String>): Boolean = scopes.containsAll(SCOPES)

    companion object {
        private const val BASE_LOGIN_URL = "https://id.twitch.tv/oauth2/authorize?response_type=token"
        private const val REDIRECT_URL = "https://flxrs.com/dankchat"
        private val SCOPES = setOf(
            "channel_editor", // TODO to be removed
            "channel_commercial", // TODO to be removed
            "channel:edit:commercial",
            "channel:manage:broadcast",
            "channel:manage:moderators",
            "channel:manage:polls",
            "channel:manage:predictions",
            "channel:manage:raids",
            "channel:manage:vips",
            "channel:moderate",
            "channel:read:polls",
            "channel:read:predictions",
            "channel:read:redemptions",
            "chat:edit",
            "chat:read",
            "moderator:manage:announcements",
            "moderator:manage:automod",
            "moderator:manage:banned_users",
            "moderator:manage:chat_messages",
            "moderator:manage:chat_settings",
            "moderator:manage:shield_mode",
            "moderator:read:chatters",
            "moderator:read:followers",
            "user:manage:blocked_users",
            "user:manage:chat_color",
            "user:manage:whispers",
            "user:read:blocked_users",
            "whispers:edit",
            "whispers:read",
        )
        const val CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
        val LOGIN_URL = "$BASE_LOGIN_URL&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URL&scope=${SCOPES.joinToString(separator = "+")}"
    }
}
