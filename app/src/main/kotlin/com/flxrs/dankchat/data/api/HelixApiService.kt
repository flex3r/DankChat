package com.flxrs.dankchat.data.api

import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthSuffix
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import retrofit2.http.GET

class HelixApiService(private val ktorClient: HttpClient, private val dankChatPreferenceStore: DankChatPreferenceStore) {

    suspend fun getUserByName(logins: List<String>): HttpResponse? = ktorClient.get("users/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        logins.forEach {
            parameter("login", it)
        }
    }

    suspend fun getUserById(userId: String): HttpResponse? = ktorClient.get("users/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("id", userId)
    }

    suspend fun getUsersFollows(fromId: String, toId: String): HttpResponse? = ktorClient.get("users/follows") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("from_id", fromId)
        parameter("to_id", toId)
    }

    suspend fun getStreams(channels: List<String>): HttpResponse? = ktorClient.get("streams/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        channels.forEach {
            parameter("user_login", it)
        }
    }

    suspend fun getUserBlocks(userId: String, first: Int = 100): HttpResponse? = ktorClient.get("users/blocks/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", userId)
        parameter("first", first)
    }

    suspend fun putUserBlock(targetUserId: String): HttpResponse? = ktorClient.put("users/blocks/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("target_user_id", targetUserId)
    }

    suspend fun deleteUserBlock(targetUserId: String): HttpResponse? = ktorClient.delete("users/blocks/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("target_user_id", targetUserId)
    }

    suspend fun getChannelBadges(userId: String): HttpResponse? = ktorClient.get("chat/badges") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", userId)
    }

    suspend fun getGlobalBadges(): HttpResponse? = ktorClient.get("chat/badges/global") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
    }
}