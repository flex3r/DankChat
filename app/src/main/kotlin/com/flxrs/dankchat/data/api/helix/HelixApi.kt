package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.dto.*
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthSuffix
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class HelixApi(private val ktorClient: HttpClient, private val dankChatPreferenceStore: DankChatPreferenceStore) {

    suspend fun getUsersByName(logins: List<UserName>): HttpResponse? = ktorClient.get("users") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        logins.forEach {
            parameter("login", it)
        }
    }

    suspend fun getUsersByIds(ids: List<UserId>): HttpResponse? = ktorClient.get("users") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        ids.forEach {
            parameter("id", it)
        }
    }

    suspend fun getUserById(userId: UserId): HttpResponse? = getUsersByIds(listOf(userId))

    suspend fun getUsersFollows(fromId: UserId, toId: UserId): HttpResponse? = ktorClient.get("users/follows") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("from_id", fromId)
        parameter("to_id", toId)
    }

    suspend fun getStreams(channels: List<UserName>): HttpResponse? = ktorClient.get("streams") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        channels.forEach {
            parameter("user_login", it)
        }
    }

    suspend fun getUserBlocks(userId: UserId, first: Int, after: String? = null): HttpResponse? = ktorClient.get("users/blocks") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", userId)
        parameter("first", first)
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun putUserBlock(targetUserId: UserId): HttpResponse? = ktorClient.put("users/blocks") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("target_user_id", targetUserId)
    }

    suspend fun deleteUserBlock(targetUserId: UserId): HttpResponse? = ktorClient.delete("users/blocks") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("target_user_id", targetUserId)
    }

    suspend fun postAnnouncement(broadcasterUserId: UserId, moderatorUserId: UserId, request: AnnouncementRequestDto): HttpResponse? = ktorClient.post("chat/announcements") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun getModerators(broadcasterUserId: UserId, first: Int, after: String? = null): HttpResponse? = ktorClient.get("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("first", first)
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun postModerator(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.post("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("user_id", userId)
    }

    suspend fun deleteModerator(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.delete("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("user_id", userId)
    }

    suspend fun postWhisper(fromUserId: UserId, toUserId: UserId, request: WhisperRequestDto): HttpResponse? = ktorClient.post("whispers") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("from_user_id", fromUserId)
        parameter("to_user_id", toUserId)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun getVips(broadcasterUserId: UserId, first: Int, after: String? = null): HttpResponse? = ktorClient.get("channels/vips") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("first", first)
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun postVip(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.post("channels/vips") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("user_id", userId)
    }

    suspend fun deleteVip(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.delete("channels/vips") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("user_id", userId)
    }

    suspend fun postBan(broadcasterUserId: UserId, moderatorUserId: UserId, request: BanRequestDto): HttpResponse? = ktorClient.post("moderation/bans") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun deleteBan(broadcasterUserId: UserId, moderatorUserId: UserId, targetUserId: UserId): HttpResponse? = ktorClient.delete("moderation/bans") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        parameter("user_id", targetUserId)
    }

    suspend fun deleteMessages(broadcasterUserId: UserId, moderatorUserId: UserId, messageId: String?): HttpResponse? = ktorClient.delete("moderation/chat") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        if (messageId != null) {
            parameter("message_id", messageId)
        }
    }

    suspend fun putUserChatColor(targetUserId: UserId, color: String): HttpResponse? = ktorClient.put("chat/color") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        parameter("user_id", targetUserId)
        parameter("color", color)
    }

    suspend fun postMarker(request: MarkerRequestDto): HttpResponse? = ktorClient.post("streams/markers") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun postCommercial(request: CommercialRequestDto): HttpResponse? = ktorClient.post("channels/commercial") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthSuffix ?: return null
        bearerAuth(oAuth)
        contentType(ContentType.Application.Json)
        setBody(request)
    }
}