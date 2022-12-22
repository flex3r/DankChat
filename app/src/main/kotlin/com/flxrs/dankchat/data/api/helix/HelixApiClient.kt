package com.flxrs.dankchat.data.api.helix

import com.flxrs.dankchat.data.api.helix.dto.*
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HelixApiClient @Inject constructor(private val helixApi: HelixApi) {

    suspend fun getUserIdByName(name: String): Result<String> = runCatching {
        helixApi.getUserByName(listOf(name))
            .throwHelixApiErrorOnFailure()
            .body<UsersDto>()
            .data.first().id
    }

    suspend fun getUsersByNames(names: List<String>): Result<List<UserDto>> = runCatching {
        helixApi.getUserByName(names)
            .throwHelixApiErrorOnFailure()
            .body<UsersDto>()
            .data
    }

    suspend fun getUsersByIds(ids: List<String>): Result<List<UserDto>> = runCatching {
        helixApi.getUsersByIds(ids)
            .throwHelixApiErrorOnFailure()
            .body<UsersDto>()
            .data
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

    suspend fun getStreams(channels: List<String>): Result<StreamsDto> = runCatching {
        helixApi.getStreams(channels)
            .throwHelixApiErrorOnFailure()
            .body()
    }

    suspend fun getUserBlocks(userId: String): Result<UserBlocksDto> = runCatching {
        helixApi.getUserBlocks(userId)
            .throwHelixApiErrorOnFailure()
            .body()
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

    private suspend fun HttpResponse?.throwHelixApiErrorOnFailure(): HttpResponse {
        this ?: throw HelixApiException(HelixError.NotLoggedIn, HttpStatusCode.Unauthorized)
        if (status.isSuccess()) {
            return this
        }

        val errorBody = runCatching { body<HelixErrorDto>() }.getOrNull()
        val error = when (status) {
            HttpStatusCode.BadRequest   -> HelixError.BadRequest
            HttpStatusCode.Forbidden    -> HelixError.Forbidden
            HttpStatusCode.Unauthorized -> when {
                errorBody?.message?.startsWith("Missing scope", ignoreCase = true) == true -> HelixError.MissingScopes
                else                                                                       -> HelixError.Unauthorized
            }

            else                        -> HelixError.Unknown
        }
        throw HelixApiException(error, status, errorBody?.message)
    }
}