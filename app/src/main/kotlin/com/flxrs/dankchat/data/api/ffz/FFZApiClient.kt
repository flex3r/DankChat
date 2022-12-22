package com.flxrs.dankchat.data.api.ffz

import com.flxrs.dankchat.data.api.ffz.dto.FFZChannelDto
import com.flxrs.dankchat.data.api.ffz.dto.FFZGlobalDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FFZApiClient @Inject constructor(private val ffzApi: FFZApi) {

    suspend fun getFFZChannelEmotes(channelId: String): Result<FFZChannelDto> = runCatching {
        ffzApi.getChannelEmotes(channelId)
            .throwApiErrorOnFailure()
            .body()
    }

    suspend fun getFFZGlobalEmotes(): Result<FFZGlobalDto> = runCatching {
        ffzApi.getGlobalEmotes()
            .throwApiErrorOnFailure()
            .body()
    }
}