package com.flxrs.dankchat.data.api.badges

import com.flxrs.dankchat.data.UserId
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class BadgesApi(private val ktorClient: HttpClient) {

    suspend fun getGlobalBadges() = ktorClient.get("global/display")

    suspend fun getChannelBadges(channelId: UserId) = ktorClient.get("channels/$channelId/display")
}