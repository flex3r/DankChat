package com.flxrs.dankchat.data.api.bttv

import com.flxrs.dankchat.data.UserId
import io.ktor.client.*
import io.ktor.client.request.*

class BTTVApi(private val ktorClient: HttpClient) {

    suspend fun getChannelEmotes(channelId: UserId) = ktorClient.get("users/twitch/$channelId")

    suspend fun getGlobalEmotes() = ktorClient.get("emotes/global")
}