package com.flxrs.dankchat.data.api.ffz

import io.ktor.client.*
import io.ktor.client.request.*

class FFZApi(private val ktorClient: HttpClient) {

    suspend fun getChannelEmotes(channelId: String) = ktorClient.get("room/id/$channelId")

    suspend fun getGlobalEmotes() = ktorClient.get("set/global")
}