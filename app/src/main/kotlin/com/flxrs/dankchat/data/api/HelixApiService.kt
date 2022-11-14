package com.flxrs.dankchat.data.api

import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthSuffix
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class HelixApiService(private val ktorClient: HttpClient, private val dankChatPreferenceStore: DankChatPreferenceStore) {

    suspend fun getUserByName(logins: List<String>): HttpResponse? = ktorClient.get("users/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        logins.forEach {
            parameter("login", it)
        }
    }

    suspend fun getUsersByIds(ids: List<String>): HttpResponse? = ktorClient.get("users/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        ids.forEach {
            parameter("id", it)
        }

    }

    suspend fun getUserById(userId: String): HttpResponse? = getUsersByIds(listOf(userId))

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
}