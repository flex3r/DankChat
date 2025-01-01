package com.flxrs.dankchat.data.api.badges

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.api.badges.dto.TwitchBadgeSetsDto
import com.flxrs.dankchat.data.api.recoverNotFoundWith
import com.flxrs.dankchat.data.api.throwApiErrorOnFailure
import io.ktor.client.call.body
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class BadgesApiClient(private val badgesApi: BadgesApi, private val json: Json) {

    suspend fun getChannelBadges(channelId: UserId): Result<TwitchBadgeSetsDto> = runCatching<BadgesApiClient, TwitchBadgeSetsDto> {
        badgesApi.getChannelBadges(channelId)
            .throwApiErrorOnFailure(json)
            .body()
    }.recoverNotFoundWith(TwitchBadgeSetsDto(sets = emptyMap()))

    suspend fun getGlobalBadges(): Result<TwitchBadgeSetsDto> = runCatching<BadgesApiClient, TwitchBadgeSetsDto> {
        badgesApi.getGlobalBadges()
            .throwApiErrorOnFailure(json)
            .body()
    }.recoverNotFoundWith(TwitchBadgeSetsDto(sets = emptyMap()))
}
