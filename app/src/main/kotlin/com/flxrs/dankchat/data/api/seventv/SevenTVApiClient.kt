package com.flxrs.dankchat.data.api.seventv

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.api.seventv.dto.SevenTVEmoteDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SevenTVApiClient @Inject constructor(private val sevenTVApi: SevenTVApi, private val json: Json) {

    suspend fun getSevenTVChannelEmotes(channelId: UserId): Result<List<SevenTVEmoteDto>> {
        return runCatching<SevenTVApiClient, List<SevenTVEmoteDto>> {
            sevenTVApi.getChannelEmotes(channelId)
                .throwApiErrorOnFailure(json)
                .body()
        }.recoverCatching {
            when {
                it is ApiException && it.status == HttpStatusCode.NotFound -> emptyList()
                else                                                       -> throw it
            }
        }
    }

    suspend fun getSevenTVGlobalEmotes(): Result<List<SevenTVEmoteDto>> = runCatching {
        sevenTVApi.getGlobalEmotes()
            .throwApiErrorOnFailure(json)
            .body()
    }
}