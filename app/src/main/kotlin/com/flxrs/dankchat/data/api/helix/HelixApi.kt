package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementRequestDto
import com.flxrs.dankchat.data.api.helix.dto.BanRequestDto
import com.flxrs.dankchat.data.api.helix.dto.ChatSettingsRequestDto
import com.flxrs.dankchat.data.api.helix.dto.CommercialRequestDto
import com.flxrs.dankchat.data.api.helix.dto.MarkerRequestDto
import com.flxrs.dankchat.data.api.helix.dto.ShieldModeRequestDto
import com.flxrs.dankchat.data.api.helix.dto.WhisperRequestDto
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.utils.extensions.withoutOAuthPrefix
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class HelixApi(private val ktorClient: HttpClient, private val dankChatPreferenceStore: DankChatPreferenceStore) {

    suspend fun getUsersByName(logins: List<UserName>): HttpResponse? = ktorClient.get("users") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        logins.forEach {
            parameter("login", it)
        }
    }

    suspend fun getUsersByIds(ids: List<UserId>): HttpResponse? = ktorClient.get("users") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        ids.forEach {
            parameter("id", it)
        }
    }

    suspend fun getChannelFollowers(broadcasterUserId: UserId, targetUserId: UserId? = null, first: Int? = null, after: String? = null): HttpResponse? = ktorClient.get("channels/followers") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        if (targetUserId != null) {
            parameter("user_id", targetUserId)
        }
        if (first != null) {
            parameter("first", first)
        }
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun getStreams(channels: List<UserName>): HttpResponse? = ktorClient.get("streams") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        channels.forEach {
            parameter("user_login", it)
        }
    }

    suspend fun getUserBlocks(userId: UserId, first: Int, after: String? = null): HttpResponse? = ktorClient.get("users/blocks") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", userId)
        parameter("first", first)
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun putUserBlock(targetUserId: UserId): HttpResponse? = ktorClient.put("users/blocks") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("target_user_id", targetUserId)
    }

    suspend fun deleteUserBlock(targetUserId: UserId): HttpResponse? = ktorClient.delete("users/blocks") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("target_user_id", targetUserId)
    }

    suspend fun postAnnouncement(broadcasterUserId: UserId, moderatorUserId: UserId, request: AnnouncementRequestDto): HttpResponse? = ktorClient.post("chat/announcements") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun getModerators(broadcasterUserId: UserId, first: Int, after: String? = null): HttpResponse? = ktorClient.get("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("first", first)
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun postModerator(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.post("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("user_id", userId)
    }

    suspend fun deleteModerator(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.delete("moderation/moderators") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("user_id", userId)
    }

    suspend fun postWhisper(fromUserId: UserId, toUserId: UserId, request: WhisperRequestDto): HttpResponse? = ktorClient.post("whispers") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("from_user_id", fromUserId)
        parameter("to_user_id", toUserId)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun getVips(broadcasterUserId: UserId, first: Int, after: String? = null): HttpResponse? = ktorClient.get("channels/vips") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("first", first)
        if (after != null) {
            parameter("after", after)
        }
    }

    suspend fun postVip(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.post("channels/vips") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("user_id", userId)
    }

    suspend fun deleteVip(broadcasterUserId: UserId, userId: UserId): HttpResponse? = ktorClient.delete("channels/vips") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("user_id", userId)
    }

    suspend fun postBan(broadcasterUserId: UserId, moderatorUserId: UserId, request: BanRequestDto): HttpResponse? = ktorClient.post("moderation/bans") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun deleteBan(broadcasterUserId: UserId, moderatorUserId: UserId, targetUserId: UserId): HttpResponse? = ktorClient.delete("moderation/bans") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        parameter("user_id", targetUserId)
    }

    suspend fun deleteMessages(broadcasterUserId: UserId, moderatorUserId: UserId, messageId: String?): HttpResponse? = ktorClient.delete("moderation/chat") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        if (messageId != null) {
            parameter("message_id", messageId)
        }
    }

    suspend fun putUserChatColor(targetUserId: UserId, color: String): HttpResponse? = ktorClient.put("chat/color") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("user_id", targetUserId)
        parameter("color", color)
    }

    suspend fun postMarker(request: MarkerRequestDto): HttpResponse? = ktorClient.post("streams/markers") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun postCommercial(request: CommercialRequestDto): HttpResponse? = ktorClient.post("channels/commercial") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun postRaid(broadcasterUserId: UserId, targetUserId: UserId): HttpResponse? = ktorClient.post("raids") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("from_broadcaster_id", broadcasterUserId)
        parameter("to_broadcaster_id", targetUserId)
    }

    suspend fun deleteRaid(broadcasterUserId: UserId): HttpResponse? = ktorClient.delete("raids") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
    }

    suspend fun patchChatSettings(broadcasterUserId: UserId, moderatorUserId: UserId, request: ChatSettingsRequestDto): HttpResponse? = ktorClient.patch("chat/settings") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    suspend fun getGlobalBadges(): HttpResponse? = ktorClient.get("chat/badges/global") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        contentType(ContentType.Application.Json)
    }

    suspend fun getChannelBadges(broadcasterUserId: UserId): HttpResponse? = ktorClient.get("chat/badges") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        contentType(ContentType.Application.Json)
    }

    suspend fun postShoutout(broadcasterUserId: UserId, targetUserId: UserId, moderatorUserId: UserId): HttpResponse? = ktorClient.post("chat/shoutouts") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("from_broadcaster_id", broadcasterUserId)
        parameter("to_broadcaster_id", targetUserId)
        parameter("moderator_id", moderatorUserId)
        contentType(ContentType.Application.Json)
    }

    suspend fun putShieldMode(broadcasterUserId: UserId, moderatorUserId: UserId, request: ShieldModeRequestDto): HttpResponse? = ktorClient.put("moderation/shield_mode") {
        val oAuth = dankChatPreferenceStore.oAuthKey?.withoutOAuthPrefix ?: return null
        bearerAuth(oAuth)
        parameter("broadcaster_id", broadcasterUserId)
        parameter("moderator_id", moderatorUserId)
        contentType(ContentType.Application.Json)
        setBody(request)
    }
}
