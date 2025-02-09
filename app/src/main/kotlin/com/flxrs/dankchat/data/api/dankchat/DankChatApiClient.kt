package com.flxrs.dankchat.data.api.dankchat

import com.flxrs.dankchat.data.api.dankchat.dto.DankChatBadgeDto
import com.flxrs.dankchat.data.api.dankchat.dto.DankChatEmoteSetDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class DankChatApiClient(private val dankChatApi: DankChatApi, private val json: Json) {

    suspend fun getUserSets(sets: List<String>): Result<List<DankChatEmoteSetDto>> = runCatching {
        dankChatApi.getSets(sets.joinToString(separator = ","))
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getDankChatBadges(): Result<List<DankChatBadgeDto>> = runCatching {
        dankChatApi.getDankChatBadges()
            .throwApiErrorOnFailure(json)
            .body()
    }
}
