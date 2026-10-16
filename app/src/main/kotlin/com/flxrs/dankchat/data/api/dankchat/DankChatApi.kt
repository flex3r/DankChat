package com.flxrs.dankchat.data.api.dankchat

import com.flxrs.dankchat.data.api.dankchat.dto.DankChatBadgeChoiceDto
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class DankChatApi(
    private val ktorClient: HttpClient,
    private val authDataStore: AuthDataStore,
    private val startupValidationHolder: StartupValidationHolder,
) {
    private fun getValidToken(): String? {
        if (!startupValidationHolder.isAuthAvailable) return null
        return authDataStore.oAuthKey?.withoutOAuthPrefix
    }

    suspend fun getDankChatBadges() = ktorClient.get("badges")

    suspend fun getAccount(): HttpResponse? = ktorClient.get("me") {
        val oAuth = getValidToken() ?: return null
        bearerAuth(oAuth)
    }

    suspend fun chooseBadge(choice: DankChatBadgeChoiceDto): HttpResponse? = ktorClient.put("me/badge") {
        val oAuth = getValidToken() ?: return null
        bearerAuth(oAuth)
        contentType(ContentType.Application.Json)
        setBody(choice)
    }

    suspend fun createLoginCode(): HttpResponse? = ktorClient.post("me/login-code") {
        val oAuth = getValidToken() ?: return null
        bearerAuth(oAuth)
    }
}
