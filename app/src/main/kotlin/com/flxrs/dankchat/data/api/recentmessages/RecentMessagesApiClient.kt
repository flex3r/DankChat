package com.flxrs.dankchat.data.api.recentmessages

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.recentmessages.dto.RecentMessagesDto
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.koin.core.annotation.Single

@Single
class RecentMessagesApiClient(
    private val recentMessagesApi: RecentMessagesApi,
    private val preferenceStore: DankChatPreferenceStore,
) {

    suspend fun getRecentMessages(channel: UserName): Result<RecentMessagesDto> = runCatching {
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
        val betterStatus = HttpStatusCode.fromValue(status.value)
        val message = errorBody?.error ?: betterStatus.description
        val error = when (errorBody?.errorCode) {
            RecentMessagesDto.ERROR_CHANNEL_NOT_JOINED -> RecentMessagesError.ChannelNotJoined
            RecentMessagesDto.ERROR_CHANNEL_IGNORED    -> RecentMessagesError.ChannelIgnored
            else                                       -> RecentMessagesError.Unknown
        }
        throw RecentMessagesApiException(error, betterStatus, request.url, message)
    }
}
