package com.flxrs.dankchat.data.api.ffz

import com.flxrs.dankchat.data.UserId
import io.ktor.client.*
import io.ktor.client.request.*

class FFZApi(private val ktorClient: HttpClient) {

    suspend fun getChannelEmotes(channelId: UserId) = ktorClient.get("room/id/$channelId")

    suspend fun getGlobalEmotes() = ktorClient.get("set/global")
}