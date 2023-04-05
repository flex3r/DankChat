package com.flxrs.dankchat.data.api.badges

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.badges.dto.TwitchBadgeSetsDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// TODO deprecate
@Singleton
class BadgesApiClient @Inject constructor(private val badgesApi: BadgesApi, private val json: Json) {

    suspend fun getChannelBadges(channelId: UserId): Result<TwitchBadgeSetsDto> = runCatching {
        badgesApi.getChannelBadges(channelId)
            .throwApiErrorOnFailure(json)
            .body()
    }

    suspend fun getGlobalBadges(): Result<TwitchBadgeSetsDto> = runCatching {
        badgesApi.getGlobalBadges()
            .throwApiErrorOnFailure(json)
            .body()
    }
}
