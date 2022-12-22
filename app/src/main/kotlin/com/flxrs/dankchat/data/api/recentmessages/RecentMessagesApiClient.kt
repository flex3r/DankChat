package com.flxrs.dankchat.data.api.recentmessages

import com.flxrs.dankchat.data.api.recentmessages.dto.RecentMessagesDto
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentMessagesApiClient @Inject constructor(
    private val recentMessagesApi: RecentMessagesApi,
    private val preferenceStore: DankChatPreferenceStore,
) {

    suspend fun getRecentMessages(channel: String): Result<RecentMessagesDto> = runCatching {
        val limit = preferenceStore.scrollbackLength
        recentMessagesApi.getRecentMessages(channel, limit)
            .throwRecentMessagesErrorOnFailure()
            .body()
    }

    private suspend fun HttpResponse.throwRecentMessagesErrorOnFailure(): HttpResponse {
        if (status.isSuccess()) {
            return this
        }

        val errorBody = runCatching { body<RecentMessagesDto>() }.getOrNull()
        val message = errorBody?.error ?: status.description
        val error = when (errorBody?.errorCode) {
            RecentMessagesDto.ERROR_CHANNEL_NOT_JOINED -> RecentMessagesError.ChannelNotJoined
            RecentMessagesDto.ERROR_CHANNEL_IGNORED    -> RecentMessagesError.ChannelIgnored
            else                                       -> RecentMessagesError.Unknown
        }
        throw RecentMessagesApiException(error, status, message)
    }
}