package com.flxrs.dankchat.data.api.bttv

import com.flxrs.dankchat.data.api.bttv.dto.BTTVChannelDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVGlobalEmoteDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BTTVApiClient @Inject constructor(private val bttvApi: BTTVApi) {

    suspend fun getBTTVChannelEmotes(channelId: String): Result<BTTVChannelDto> = runCatching {
        bttvApi.getChannelEmotes(channelId)
            .throwApiErrorOnFailure()
            .body()
    }

    suspend fun getBTTVGlobalEmotes(): Result<List<BTTVGlobalEmoteDto>> = runCatching {
        bttvApi.getGlobalEmotes()
            .throwApiErrorOnFailure()
            .body()
    }
}