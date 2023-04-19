package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.dto.AnnouncementRequestDto
import com.flxrs.dankchat.data.api.helix.dto.BadgeSetDto
import com.flxrs.dankchat.data.api.helix.dto.BanRequestDto
import com.flxrs.dankchat.data.api.helix.dto.ChatSettingsDto
import com.flxrs.dankchat.data.api.helix.dto.ChatSettingsRequestDto
import com.flxrs.dankchat.data.api.helix.dto.CommercialDto
import com.flxrs.dankchat.data.api.helix.dto.CommercialRequestDto
import com.flxrs.dankchat.data.api.helix.dto.DataListDto
import com.flxrs.dankchat.data.api.helix.dto.HelixErrorDto
import com.flxrs.dankchat.data.api.helix.dto.MarkerDto
import com.flxrs.dankchat.data.api.helix.dto.MarkerRequestDto
import com.flxrs.dankchat.data.api.helix.dto.ModVipDto
import com.flxrs.dankchat.data.api.helix.dto.PagedDto
import com.flxrs.dankchat.data.api.helix.dto.RaidDto
import com.flxrs.dankchat.data.api.helix.dto.StreamDto
import com.flxrs.dankchat.data.api.helix.dto.UserBlockDto
import com.flxrs.dankchat.data.api.helix.dto.UserDto
import com.flxrs.dankchat.data.api.helix.dto.UserFollowsDto
import com.flxrs.dankchat.data.api.helix.dto.WhisperRequestDto
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelixApiClient @Inject constructor(private val helixApi: HelixApi, private val json: Json) {

    suspend fun getUsersByNames(names: List<UserName>): Result<List<UserDto>> = runCatching {
        names.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getUsersByName(it)
                .throwHelixApiErrorOnFailure()
                .body<DataListDto<UserDto>>()
                .data
        }
    }

    suspend fun getUsersByIds(ids: List<UserId>): Result<List<UserDto>> = runCatching {
        ids.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getUsersByIds(it)
                .throwHelixApiErrorOnFailure()
                .body<DataListDto<UserDto>>()
                .data
        }
    }

    suspend fun getUser(userId: UserId): Result<UserDto> = getUsersByIds(listOf(userId))
        .mapCatching { it.first() }

    suspend fun getUserByName(name: UserName): Result<UserDto> = getUsersByNames(listOf(name))
        .mapCatching { it.first() }

    suspend fun getUserIdByName(name: UserName): Result<UserId> = getUserByName(name)
        .mapCatching { it.id }

    suspend fun getChannelFollowers(broadcastUserId: UserId, targetUserId: UserId): Result<UserFollowsDto> = runCatching {
        helixApi.getChannelFollowers(broadcastUserId, targetUserId)
            .throwHelixApiErrorOnFailure()
            .body()
    }

    suspend fun getStreams(channels: List<UserName>): Result<List<StreamDto>> = runCatching {
        channels.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getStreams(it)
                .throwHelixApiErrorOnFailure()
                .body<DataListDto<StreamDto>>()
                .data
        }
    }

    suspend fun getUserBlocks(userId: UserId, maxUserBlocksToFetch: Int = 500): Result<List<UserBlockDto>> = runCatching {
        pageUntil(maxUserBlocksToFetch) { cursor ->
            helixApi.getUserBlocks(userId, DEFAULT_PAGE_SIZE, cursor)
        }
    }

    suspend fun blockUser(targetUserId: UserId): Result<Unit> = runCatching {
        helixApi.putUserBlock(targetUserId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun unblockUser(targetUserId: UserId): Result<Unit> = runCatching {
        helixApi.deleteUserBlock(targetUserId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun postAnnouncement(
        broadcastUserId: UserId,
        moderatorUserId: UserId,
        request: AnnouncementRequestDto
    ): Result<Unit> = runCatching {
        helixApi.postAnnouncement(broadcastUserId, moderatorUserId, request)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun postWhisper(
        fromUserId: UserId,
        toUserId: UserId,
        request: WhisperRequestDto
    ): Result<Unit> = runCatching {
        helixApi.postWhisper(fromUserId, toUserId, request)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun getModerators(broadcastUserId: UserId, maxModeratorsToFetch: Int = 500): Result<List<ModVipDto>> = runCatching {
        pageUntil(maxModeratorsToFetch) { cursor ->
            helixApi.getModerators(broadcastUserId, DEFAULT_PAGE_SIZE, cursor)
        }
    }

    suspend fun postModerator(broadcastUserId: UserId, userId: UserId): Result<Unit> = runCatching {
        helixApi.postModerator(broadcastUserId, userId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun deleteModerator(broadcastUserId: UserId, userId: UserId): Result<Unit> = runCatching {
        helixApi.deleteModerator(broadcastUserId, userId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun getVips(broadcastUserId: UserId, maxVipsToFetch: Int = 500): Result<List<ModVipDto>> = runCatching {
        pageUntil(maxVipsToFetch) { cursor ->
            helixApi.getVips(broadcastUserId, DEFAULT_PAGE_SIZE, cursor)
        }
    }

    suspend fun postVip(broadcastUserId: UserId, userId: UserId): Result<Unit> = runCatching {
        helixApi.postVip(broadcastUserId, userId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun deleteVip(broadcastUserId: UserId, userId: UserId): Result<Unit> = runCatching {
        helixApi.deleteVip(broadcastUserId, userId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun postBan(broadcastUserId: UserId, moderatorUserId: UserId, requestDto: BanRequestDto): Result<Unit> = runCatching {
        helixApi.postBan(broadcastUserId, moderatorUserId, requestDto)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun deleteBan(broadcastUserId: UserId, moderatorUserId: UserId, targetUserId: UserId): Result<Unit> = runCatching {
        helixApi.deleteBan(broadcastUserId, moderatorUserId, targetUserId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun deleteMessages(broadcastUserId: UserId, moderatorUserId: UserId, messageId: String? = null): Result<Unit> = runCatching {
        helixApi.deleteMessages(broadcastUserId, moderatorUserId, messageId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun putUserChatColor(targetUserId: UserId, color: String): Result<Unit> = runCatching {
        helixApi.putUserChatColor(targetUserId, color)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun postMarker(requestDto: MarkerRequestDto): Result<MarkerDto> = runCatching {
        helixApi.postMarker(requestDto)
            .throwHelixApiErrorOnFailure()
            .body<DataListDto<MarkerDto>>()
            .data
            .first()
    }

    suspend fun postCommercial(request: CommercialRequestDto): Result<CommercialDto> = runCatching {
        helixApi.postCommercial(request)
            .throwHelixApiErrorOnFailure()
            .body<DataListDto<CommercialDto>>()
            .data
            .first()
    }

    suspend fun postRaid(broadcastUserId: UserId, targetUserId: UserId): Result<RaidDto> = runCatching {
        helixApi.postRaid(broadcastUserId, targetUserId)
            .throwHelixApiErrorOnFailure()
            .body<DataListDto<RaidDto>>()
            .data
            .first()
    }

    suspend fun deleteRaid(broadcastUserId: UserId): Result<Unit> = runCatching {
        helixApi.deleteRaid(broadcastUserId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun patchChatSettings(broadcastUserId: UserId, moderatorUserId: UserId, request: ChatSettingsRequestDto): Result<ChatSettingsDto> = runCatching {
        helixApi.patchChatSettings(broadcastUserId, moderatorUserId, request)
            .throwHelixApiErrorOnFailure()
            .body<DataListDto<ChatSettingsDto>>()
            .data
            .first()
    }

    suspend fun getGlobalBadges(): Result<List<BadgeSetDto>> = runCatching {
        helixApi.getGlobalBadges()
            .throwHelixApiErrorOnFailure()
            .body<DataListDto<BadgeSetDto>>()
            .data
    }

    suspend fun getChannelBadges(broadcastUserId: UserId): Result<List<BadgeSetDto>> = runCatching {
        helixApi.getChannelBadges(broadcastUserId)
            .throwHelixApiErrorOnFailure()
            .body<DataListDto<BadgeSetDto>>()
            .data
    }

    private suspend inline fun <reified T> pageUntil(amountToFetch: Int, request: (cursor: String?) -> HttpResponse?): List<T> {
        val initialPage = request(null)
            .throwHelixApiErrorOnFailure()
            .body<PagedDto<T>>()

        var cursor = initialPage.pagination.cursor
        val entries = initialPage.data.toMutableList()

        while (cursor != null && entries.size < amountToFetch) {
            val result = request(cursor)
                .throwHelixApiErrorOnFailure()
                .body<PagedDto<T>>()

            entries.addAll(result.data)
            cursor = result.pagination.cursor
        }

        return entries
    }

    private suspend fun HttpResponse?.throwHelixApiErrorOnFailure(): HttpResponse {
        this ?: throw HelixApiException(HelixError.NotLoggedIn, HttpStatusCode.Unauthorized, url = null)
        if (status.isSuccess()) {
            return this
        }

        val errorBody = json.decodeOrNull<HelixErrorDto>(bodyAsText()) ?: throw HelixApiException(HelixError.Unknown, status, request.url, status.description)
        val message = errorBody.message
        val betterStatus = HttpStatusCode.fromValue(status.value)
        val error = when (betterStatus) {
            HttpStatusCode.BadRequest          -> when {
                message.startsWith(WHISPER_SELF_ERROR, ignoreCase = true)           -> HelixError.WhisperSelf
                message.startsWith(USER_ALREADY_MOD_ERROR, ignoreCase = true)       -> HelixError.TargetAlreadyModded
                message.startsWith(USER_NOT_MOD_ERROR, ignoreCase = true)           -> HelixError.TargetNotModded
                message.startsWith(USER_ALREADY_BANNED_ERROR, ignoreCase = true)    -> HelixError.TargetAlreadyBanned
                message.startsWith(USER_MAY_NOT_BE_BANNED_ERROR, ignoreCase = true) -> HelixError.TargetCannotBeBanned
                message.startsWith(USER_NOT_BANNED_ERROR, ignoreCase = true)        -> HelixError.TargetNotBanned
                message.startsWith(INVALID_COLOR_ERROR, ignoreCase = true)          -> HelixError.InvalidColor
                message.startsWith(BROADCASTER_NOT_LIVE_ERROR, ignoreCase = true)   -> HelixError.BroadcasterNotStreaming
                message.startsWith(MISSING_REQUIRED_PARAM_ERROR, ignoreCase = true) -> HelixError.MissingLengthParameter
                message.startsWith(RAID_SELF_ERROR, ignoreCase = true)              -> HelixError.RaidSelf
                message.contains(NOT_IN_RANGE_ERROR, ignoreCase = true)             -> {
                    val match = INVALID_RANGE_REGEX.find(message)?.groupValues
                    val start = match?.getOrNull(1)?.toIntOrNull()
                    val end = match?.getOrNull(2)?.toIntOrNull()
                    when {
                        start != null && end != null -> HelixError.NotInRange(validRange = start..end)
                        else                         -> HelixError.NotInRange(validRange = null)
                    }
                }

                else                                                                -> HelixError.Forwarded
            }

            HttpStatusCode.Forbidden           -> when {
                message.startsWith(RECIPIENT_BLOCKED_USER_ERROR, ignoreCase = true) -> HelixError.RecipientBlockedUser
                else                                                                -> HelixError.UserNotAuthorized
            }

            HttpStatusCode.Unauthorized        -> when {
                message.startsWith(MISSING_SCOPE_ERROR, ignoreCase = true)           -> HelixError.MissingScopes
                message.startsWith(NO_VERIFIED_PHONE_ERROR, ignoreCase = true)       -> HelixError.NoVerifiedPhone
                message.startsWith(BROADCASTER_OAUTH_TOKEN_ERROR, ignoreCase = true) -> HelixError.BroadcasterTokenRequired
                message.startsWith(USER_AUTH_ERROR, ignoreCase = true)               -> HelixError.UserNotAuthorized
                else                                                                 -> HelixError.Forwarded
            }

            HttpStatusCode.NotFound            -> when (request.url.encodedPath) {
                "/helix/streams/markers" -> HelixError.MarkerError(message.substringAfter("message:\"", "").substringBeforeLast('"').ifBlank { null })
                "helix/raids"            -> HelixError.NoRaidPending
                else                     -> HelixError.Forwarded
            }

            HttpStatusCode.UnprocessableEntity -> when (request.url.encodedPath) {
                "/helix/moderation/moderators" -> HelixError.TargetIsVip
                else                           -> HelixError.Forwarded
            }

            HttpStatusCode.TooManyRequests     -> when (request.url.encodedPath) {
                "/helix/whispers"            -> HelixError.WhisperRateLimited
                "/helix/channels/commercial" -> HelixError.CommercialRateLimited
                else                         -> HelixError.Forwarded
            }

            HttpStatusCode.Conflict            -> when (request.url.encodedPath) {
                "helix/moderation/bans" -> HelixError.ConflictingBanOperation
                else                    -> HelixError.Forwarded
            }

            HttpStatusCode.TooEarly            -> HelixError.Forwarded
            else                               -> HelixError.Unknown
        }
        throw HelixApiException(error, betterStatus, request.url, message)
    }

    companion object {
        private val TAG = HelixApiClient::class.java.simpleName
        private const val DEFAULT_PAGE_SIZE = 100
        private const val WHISPER_SELF_ERROR = "A user cannot whisper themself"
        private const val MISSING_SCOPE_ERROR = "Missing scope"
        private const val NO_VERIFIED_PHONE_ERROR = "the sender does not have a verified phone number"
        private const val RECIPIENT_BLOCKED_USER_ERROR = "The recipient's settings prevent this sender from whispering them"
        private const val BROADCASTER_OAUTH_TOKEN_ERROR = "The ID in broadcaster_id"
        private const val USER_AUTH_ERROR = "incorrect user authorization"
        private const val USER_ALREADY_MOD_ERROR = "user is already a mod"
        private const val USER_NOT_MOD_ERROR = "user is not a mod"
        private const val USER_NOT_BANNED_ERROR = "The user in the user_id query parameter is not banned"
        private const val USER_ALREADY_BANNED_ERROR = "The user specified in the user_id field is already banned"
        private const val USER_MAY_NOT_BE_BANNED_ERROR = "The user specified in the user_id field may not be banned"
        private const val INVALID_COLOR_ERROR = "invalid color"
        private const val BROADCASTER_NOT_LIVE_ERROR = "To start a commercial, the broadcaster must be streaming live."
        private const val MISSING_REQUIRED_PARAM_ERROR = "Missing required parameter"
        private const val RAID_SELF_ERROR = "The IDs in from_broadcaster_id and to_broadcaster_id cannot be the same."
        private const val NOT_IN_RANGE_ERROR = "must be in the range"
        private val INVALID_RANGE_REGEX = """(\d+) through (\d+)""".toRegex()
    }
}
