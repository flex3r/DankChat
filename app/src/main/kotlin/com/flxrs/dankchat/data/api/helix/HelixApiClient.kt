package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.helix.dto.*
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelixApiClient @Inject constructor(private val helixApi: HelixApi, private val json: Json) {

    suspend fun getUsersByNames(names: List<UserName>): Result<List<UserDto>> = runCatching {
        names.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getUsersByName(it)
                .throwHelixApiErrorOnFailure()
                .body<UsersDto>()
                .data
        }
    }

    suspend fun getUsersByIds(ids: List<UserId>): Result<List<UserDto>> = runCatching {
        ids.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getUsersByIds(it)
                .throwHelixApiErrorOnFailure()
                .body<UsersDto>()
                .data
        }
    }

    suspend fun getUser(userId: UserId): Result<UserDto> = runCatching {
        helixApi.getUserById(userId)
            .throwHelixApiErrorOnFailure()
            .body<UsersDto>()
            .data.first()
    }

    suspend fun getUserByName(name: UserName): Result<UserDto> = runCatching {
        helixApi.getUsersByName(listOf(name))
            .throwHelixApiErrorOnFailure()
            .body<UsersDto>()
            .data.first()
    }

    suspend fun getUserIdByName(name: UserName): Result<UserId> = getUserByName(name)
        .mapCatching { it.id }

    suspend fun getUsersFollows(fromId: UserId, toId: UserId): Result<UserFollowsDto> = runCatching {
        helixApi.getUsersFollows(fromId, toId)
            .throwHelixApiErrorOnFailure()
            .body()
    }

    suspend fun getStreams(channels: List<UserName>): Result<List<StreamDto>> = runCatching {
        channels.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getStreams(it)
                .throwHelixApiErrorOnFailure()
                .body<StreamsDto>()
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
                message.startsWith(WHISPER_SELF_ERROR, ignoreCase = true)     -> HelixError.WhisperSelf
                message.startsWith(USER_ALREADY_MOD_ERROR, ignoreCase = true) -> HelixError.TargetAlreadyModded
                message.startsWith(USER_NOT_MOD_ERROR, ignoreCase = true)     -> HelixError.TargetNotModded
                else                                                          -> HelixError.Forwarded
            }

            HttpStatusCode.Forbidden           -> when {
                message.startsWith(RECIPIENT_BLOCKED_USER_ERROR, ignoreCase = true) -> HelixError.RecipientBlockedUser
                else                                                                -> HelixError.Forbidden
            }

            HttpStatusCode.Unauthorized        -> when {
                message.startsWith(MISSING_SCOPE_ERROR, ignoreCase = true)           -> HelixError.MissingScopes
                message.startsWith(NO_VERIFIED_PHONE_ERROR, ignoreCase = true)       -> HelixError.NoVerifiedPhone
                message.startsWith(BROADCASTER_OAUTH_TOKEN_ERROR, ignoreCase = true) -> HelixError.BroadcasterTokenRequired
                message.startsWith(USER_AUTH_ERROR, ignoreCase = true)               -> HelixError.UserNotAuthorized
                else                                                                 -> HelixError.Forwarded
            }


            HttpStatusCode.UnprocessableEntity -> when (request.url.encodedPath) {
                "/helix/moderation/moderators" -> HelixError.TargetIsVip
                else                           -> HelixError.Forwarded
            }

            HttpStatusCode.TooManyRequests     -> when (request.url.encodedPath) {
                "/helix/whispers/" -> HelixError.WhisperRateLimited
                else               -> HelixError.Forwarded
            }

            HttpStatusCode.Conflict            -> HelixError.Forwarded
            TOO_EARLY_STATUS                   -> HelixError.Forwarded
            else                               -> HelixError.Unknown
        }
        throw HelixApiException(error, betterStatus, request.url, message)
    }

    companion object {
        private val TOO_EARLY_STATUS = HttpStatusCode(425, "Too Early")
        private const val DEFAULT_PAGE_SIZE = 100
        private const val WHISPER_SELF_ERROR = "A user cannot whisper themself"
        private const val MISSING_SCOPE_ERROR = "Missing scope"
        private const val NO_VERIFIED_PHONE_ERROR = "the sender does not have a verified phone number"
        private const val RECIPIENT_BLOCKED_USER_ERROR = "The recipient's settings prevent this sender from whispering them"
        private const val BROADCASTER_OAUTH_TOKEN_ERROR = "The ID in broadcaster_id"
        private const val USER_AUTH_ERROR = "incorrect user authorization"
        private const val USER_ALREADY_MOD_ERROR = "user is already a mod"
        private const val USER_NOT_MOD_ERROR = "user is not a mod"
    }
}