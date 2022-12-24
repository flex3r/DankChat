package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementRequestDto
import com.flxrs.dankchat.data.api.helix.dto.WhisperRequestDto
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthSuffix
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class HelixApi(private val ktorClient: HttpClient, private val dankChatPreferenceStore: DankChatPreferenceStore) {

    suspend fun getUsersByName(logins: List<UserName>): HttpResponse? = ktorClient.get("users/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        logins.forEach {
            parameter("login", it.value)
        }
    }

    suspend fun getUsersByIds(ids: List<UserId>): HttpResponse? = ktorClient.get("users/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        ids.forEach {
            parameter("id", it.value)
        }
    }

    suspend fun getUserById(userId: UserId): HttpResponse? = getUsersByIds(listOf(userId))

    suspend fun getUsersFollows(fromId: UserId, toId: UserId): HttpResponse? = ktorClient.get("users/follows") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("from_id", fromId.value)
        parameter("to_id", toId.value)
    }

    suspend fun getStreams(channels: List<UserName>): HttpResponse? = ktorClient.get("streams/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        channels.forEach {
            parameter("user_login", it.value)
        }
    }

    suspend fun getUserBlocks(userId: UserId, first: Int, after: String? = null): HttpResponse? = ktorClient.get("users/blocks/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", userId.value)
        parameter("first", first)
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun putUserBlock(targetUserId: UserId): HttpResponse? = ktorClient.put("users/blocks/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("target_user_id", targetUserId.value)
    }

    suspend fun deleteUserBlock(targetUserId: UserId): HttpResponse? = ktorClient.delete("users/blocks/") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("target_user_id", targetUserId.value)
    }

    suspend fun postAnnouncement(broadcasterUserId: UserId, moderatorUserId: UserId, request: AnnouncementRequestDto): HttpResponse? = ktorClient.post("chat/announcements") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId.value)
        parameter("moderator_id", moderatorUserId.value)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun getModerators(broadcasterUserId: UserId, first: Int, after: String? = null): HttpResponse? = ktorClient.get("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId.value)
        parameter("first", first)
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun postModerator(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.post("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId.value)
        parameter("user_id", userId.value)
    }

    suspend fun deleteModerator(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.delete("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId.value)
        parameter("user_id", userId.value)
    }

    suspend fun postWhisper(fromUserId: UserId, toUserId: UserId, request: WhisperRequestDto): HttpResponse? = ktorClient.post("whispers") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("from_user_id", fromUserId.value)
        parameter("to_user_id", toUserId.value)
        contentType(ContentType.Application.Json)
        setBody(request)
    }
}