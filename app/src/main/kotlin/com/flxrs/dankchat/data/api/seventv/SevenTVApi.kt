package com.flxrs.dankchat.data.api.seventv

import io.ktor.client.*
import io.ktor.client.request.*

class SevenTVApi(private val ktorClient: HttpClient) {

    suspend fun getChannelEmotes(channelId: String) = ktorClient.get("users/$channelId/emotes")

    suspend fun getGlobalEmotes() = ktorClient.get("emotes/global")
}