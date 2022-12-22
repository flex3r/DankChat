package com.flxrs.dankchat.data.api.seventv

import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SevenTVApiClient @Inject constructor(private val sevenTVApi: SevenTVApi) {

    suspend fun getSevenTVChannelEmotes(channelId: String): Result<List<SevenTVEmoteDto>> = runCatching {
        sevenTVApi.getChannelEmotes(channelId)
            .throwApiErrorOnFailure()
            .body()
    }
    suspend fun getSevenTVGlobalEmotes(): Result<List<SevenTVEmoteDto>> = runCatching {
        sevenTVApi.getGlobalEmotes()
            .throwApiErrorOnFailure()
            .body()
    }
}