package com.flxrs.dankchat.data.api

import io.ktor.client.*
import io.ktor.client.request.*

class BadgesApiService(private val ktorClient: HttpClient) {

    suspend fun getGlobalBadges() = ktorClient.get("global/display")

    suspend fun getChannelBadges(channelId: String) = ktorClient.get("channels/$channelId/display")
}