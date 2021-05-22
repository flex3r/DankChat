package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.BTTVChannelDto
import com.flxrs.dankchat.service.api.dto.BTTVGlobalEmotesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface BTTVApiService {
    @GET("users/twitch/{channelId}")
    suspend fun getChannelEmotes(@Path("channelId") channelId: String): Response<BTTVChannelDto>

    @GET("emotes/global")
    suspend fun getGlobalEmotes(): Response<List<BTTVGlobalEmotesDto>>
}