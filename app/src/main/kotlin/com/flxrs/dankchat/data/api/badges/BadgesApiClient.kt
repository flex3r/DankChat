package com.flxrs.dankchat.data.api.badges

import com.flxrs.dankchat.data.api.badges.dto.TwitchBadgeSetsDto
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BadgesApiClient @Inject constructor(private val badgesApi: BadgesApi) {

    suspend fun getChannelBadges(channelId: String): Result<TwitchBadgeSetsDto> = runCatching {
        badgesApi.getChannelBadges(channelId)
            .throwApiErrorOnFailure()
            .body()
    }
    suspend fun getGlobalBadges(): Result<TwitchBadgeSetsDto> = runCatching {
        badgesApi.getGlobalBadges()
            .throwApiErrorOnFailure()
            .body()
    }
}