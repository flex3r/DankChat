package com.flxrs.dankchat.data.api.bttv

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.bttv.dto.BTTVChannelDto
import com.flxrs.dankchat.data.api.bttv.dto.BTTVGlobalEmoteDto
import com.flxrs.dankchat.data.api.recoverNotFoundWith
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class BTTVApiClient(private val bttvApi: BTTVApi, private val json: Json) {

    suspend fun getBTTVChannelEmotes(channelId: UserId): Result<BTTVChannelDto?> = runCatching {
        bttvApi.getChannelEmotes(channelId)
            .throwApiErrorOnFailure(json)
            .body<BTTVChannelDto>()
    }.recoverNotFoundWith(null)

    suspend fun getBTTVGlobalEmotes(): Result<List<BTTVGlobalEmoteDto>> = runCatching {
        bttvApi.getGlobalEmotes()
            .throwApiErrorOnFailure(json)
            .body()
    }
}
