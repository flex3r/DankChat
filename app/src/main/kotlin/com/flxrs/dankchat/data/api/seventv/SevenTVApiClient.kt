package com.flxrs.dankchat.data.api.seventv

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.recoverNotFoundWith
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SevenTVApiClient @Inject constructor(private val sevenTVApi: SevenTVApi, private val json: Json) {

    suspend fun getSevenTVChannelEmotes(channelId: UserId): Result<List<SevenTVEmoteDto>> = runCatching {
        sevenTVApi.getChannelEmotes(channelId)
            .throwApiErrorOnFailure(json)
            .body<List<SevenTVEmoteDto>>()
    }.recoverNotFoundWith(emptyList())

    suspend fun getSevenTVGlobalEmotes(): Result<List<SevenTVEmoteDto>> = runCatching {
        sevenTVApi.getGlobalEmotes()
            .throwApiErrorOnFailure(json)
            .body()
    }
}