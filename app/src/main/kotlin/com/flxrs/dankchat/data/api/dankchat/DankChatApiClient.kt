package com.flxrs.dankchat.data.api.dankchat

import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatAccountDto
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatBadgeChoiceDto
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatBadgesDto
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatLoginCodeDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class DankChatApiClient(
    private val dankChatApi: DankChatApi,
    private val json: Json,
) {
    suspend fun getDankChatBadges(): Result<DankChatBadgesDto> = runCatching {
        dankChatApi
            .getDankChatBadges()
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getAccount(): Result<DankChatAccountDto> = runCatching {
        dankChatApi
            .getAccount()
            .throwAccountErrorOnFailure()
            .body()
    }

    suspend fun chooseBadge(choice: DankChatBadgeChoiceDto): Result<DankChatAccountDto> = runCatching {
        dankChatApi
            .chooseBadge(choice)
            .throwAccountErrorOnFailure()
            .body()
    }

    suspend fun createLoginCode(): Result<DankChatLoginCodeDto> = runCatching {
        dankChatApi
            .createLoginCode()
            .throwAccountErrorOnFailure()
            .body()
    }

    private suspend fun HttpResponse?.throwAccountErrorOnFailure(): HttpResponse {
        this ?: throw ApiException(HttpStatusCode.Unauthorized, url = null, message = "Not logged in")
        return throwApiErrorOnFailure(json)
    }
}
