package com.flxrs.dankchat.data.api.seventv

import com.flxrs.dankchat.data.UserId
import io.ktor.client.HttpClient
import io.ktor.client.request.get

class SevenTVApi(private val ktorClient: HttpClient) {

    suspend fun getChannelEmotes(channelId: UserId) = ktorClient.get("users/$channelId/emotes")

    suspend fun getGlobalEmotes() = ktorClient.get("emotes/global")
}