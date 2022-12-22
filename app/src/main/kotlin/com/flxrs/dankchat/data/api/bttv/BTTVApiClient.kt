package com.flxrs.dankchat.data.api.bttv

import com.flxrs.dankchat.data.api.bttv.dto.BTTVChannelDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVGlobalEmoteDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BTTVApiClient @Inject constructor(private val bttvApi: BTTVApi, private val json: Json) {

    suspend fun getBTTVChannelEmotes(channelId: String): Result<BTTVChannelDto> = runCatching {
        bttvApi.getChannelEmotes(channelId)
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getBTTVGlobalEmotes(): Result<List<BTTVGlobalEmoteDto>> = runCatching {
        bttvApi.getGlobalEmotes()
            .throwApiErrorOnFailure(json)
            .body()
    }
}