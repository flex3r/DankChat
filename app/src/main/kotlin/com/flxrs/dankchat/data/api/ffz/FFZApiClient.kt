package com.flxrs.dankchat.data.api.ffz

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.ffz.dto.FFZChannelDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZGlobalDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FFZApiClient @Inject constructor(private val ffzApi: FFZApi, private val json: Json) {

    suspend fun getFFZChannelEmotes(channelId: UserId): Result<FFZChannelDto> = runCatching {
        ffzApi.getChannelEmotes(channelId)
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getFFZGlobalEmotes(): Result<FFZGlobalDto> = runCatching {
        ffzApi.getGlobalEmotes()
            .throwApiErrorOnFailure(json)
            .body()
    }
}