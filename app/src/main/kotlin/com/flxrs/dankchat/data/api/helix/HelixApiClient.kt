package com.flxrs.dankchat.data.api.helix

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

    suspend fun getUserIdByName(name: String): Result<String> = runCatching {
        helixApi.getUserByName(listOf(name))
            .throwHelixApiErrorOnFailure()
            .body<UsersDto>()
            .data.first().id
    }

    suspend fun getUsersByNames(names: List<String>): Result<List<UserDto>> = runCatching {
        names.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getUserByName(it)
                .throwHelixApiErrorOnFailure()
                .body<UsersDto>()
                .data
        }
    }

    suspend fun getUsersByIds(ids: List<String>): Result<List<UserDto>> = runCatching {
        ids.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getUsersByIds(it)
                .throwHelixApiErrorOnFailure()
                .body<UsersDto>()
                .data
        }
    }

    suspend fun getUser(userId: String): Result<UserDto> = runCatching {
        helixApi.getUserById(userId)
            .throwHelixApiErrorOnFailure()
            .body<UsersDto>()
            .data.first()
    }

    suspend fun getUsersFollows(fromId: String, toId: String): Result<UserFollowsDto> = runCatching {
        helixApi.getUsersFollows(fromId, toId)
            .throwHelixApiErrorOnFailure()
            .body()
    }

    suspend fun getStreams(channels: List<String>): Result<List<StreamDto>> = runCatching {
        channels.chunked(DEFAULT_PAGE_SIZE).flatMap {
            helixApi.getStreams(it)
                .throwHelixApiErrorOnFailure()
                .body<StreamsDto>()
                .data
        }
    }

    suspend fun getUserBlocks(userId: String, maxUserBlocksToFetch: Int = 500): Result<List<UserBlockDto>> = runCatching {
        pageUntil(maxUserBlocksToFetch) { cursor ->
            helixApi.getUserBlocks(userId, DEFAULT_PAGE_SIZE, cursor)
        }
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> = runCatching {
        helixApi.putUserBlock(targetUserId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun unblockUser(targetUserId: String): Result<Unit> = runCatching {
        helixApi.deleteUserBlock(targetUserId)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun postAnnouncement(
        broadcastUserId: String,
        moderatorUserId: String,
        request: AnnouncementRequestDto
    ): Result<Unit> = runCatching {
        helixApi.postAnnouncement(broadcastUserId, moderatorUserId, request)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun postWhisper(
        fromUserId: String,
        toUserId: String,
        request: WhisperRequestDto
    ): Result<Unit> = runCatching {
        helixApi.postWhisper(fromUserId, toUserId, request)
            .throwHelixApiErrorOnFailure()
    }

    suspend fun getModerators(broadcastUserId: String, maxModeratorsToFetch: Int = 500): Result<List<ModDto>> = runCatching {
        pageUntil(maxModeratorsToFetch) { cursor ->
            helixApi.getModerators(broadcastUserId, DEFAULT_PAGE_SIZE, cursor)
        }
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
        this ?: throw HelixApiException(HelixError.NotLoggedIn, HttpStatusCode.Unauthorized)
        if (status.isSuccess()) {
            return this
        }

        val errorBody = json.decodeOrNull<HelixErrorDto>(bodyAsText()) ?: throw HelixApiException(HelixError.Unknown, status, status.description)
        val error = when (status) {
            HttpStatusCode.BadRequest      -> when {
                errorBody.message.startsWith(WHISPER_SELF_ERROR, ignoreCase = true) -> HelixError.WhisperSelf
                else                                                                -> HelixError.BadRequest
            }

            HttpStatusCode.Forbidden       -> when {
                errorBody.message.startsWith(RECIPIENT_BLOCKED_USER_ERROR, ignoreCase = true) -> HelixError.RecipientBlockedUser
                else                                                                          -> HelixError.Forbidden
            }

            HttpStatusCode.Unauthorized    -> when {
                errorBody.message.startsWith(MISSING_SCOPE_ERROR, ignoreCase = true)           -> HelixError.MissingScopes
                errorBody.message.startsWith(NO_VERIFIED_PHONE_ERROR, ignoreCase = true)       -> HelixError.NoVerifiedPhone
                errorBody.message.startsWith(BROADCASTER_OAUTH_TOKEN_ERROR, ignoreCase = true) -> HelixError.BroadcasterTokenRequired
                else                                                                           -> HelixError.Unauthorized
            }

            HttpStatusCode.TooManyRequests -> when (request.url.encodedPath) {
                "/helix/whispers/" -> HelixError.WhisperRateLimited
                else               -> HelixError.RateLimited
            }

            else                           -> HelixError.Unknown
        }
        throw HelixApiException(error, status, errorBody.message)
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 100
        private const val WHISPER_SELF_ERROR = "A user cannot whisper themself"
        private const val MISSING_SCOPE_ERROR = "Missing scope"
        private const val NO_VERIFIED_PHONE_ERROR = "the sender does not have a verified phone number"
        private const val RECIPIENT_BLOCKED_USER_ERROR = "The recipient's settings prevent this sender from whispering them"
        private const val BROADCASTER_OAUTH_TOKEN_ERROR = "The ID in broadcaster_id"
    }
}