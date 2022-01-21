package com.flxrs.dankchat.data.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.dto.BTTVChannelDto
import com.flxrs.dankchat.data.api.dto.BTTVGlobalEmotesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface BTTVApiService {
    @GET("users/twitch/{channelId}")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getChannelEmotes(@Path("channelId") channelId: String): Response<BTTVChannelDto>

    @GET("emotes/global")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getGlobalEmotes(): Response<List<BTTVGlobalEmotesDto>>
}