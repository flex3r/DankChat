package com.flxrs.dankchat.data.api.seventv

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.recoverNotFoundWith
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteSetDto
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVUserDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SevenTVApiClient @Inject constructor(private val sevenTVApi: SevenTVApi, private val json: Json) {

    suspend fun getSevenTVChannelEmotes(channelId: UserId): Result<SevenTVUserDto?> = runCatching {
        sevenTVApi.getChannelEmotes(channelId)
            .throwApiErrorOnFailure(json)
            .body<SevenTVUserDto>()
    }.recoverNotFoundWith(default = null)

    suspend fun getSevenTVEmoteSet(emoteSetId: String): Result<SevenTVEmoteSetDto> = runCatching {
        sevenTVApi.getEmoteSet(emoteSetId)
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getSevenTVGlobalEmotes(): Result<List<SevenTVEmoteDto>> = runCatching {
        sevenTVApi.getGlobalEmotes()
            .throwApiErrorOnFailure(json)
            .body<SevenTVEmoteSetDto>()
            .emotes
            .orEmpty()
    }
}
