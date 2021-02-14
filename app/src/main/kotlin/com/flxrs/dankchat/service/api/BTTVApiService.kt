package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.EmoteDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Url

interface BTTVApiService {
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("users/twitch/{channelId}")
    suspend fun getChannelEmotes(@Path("channelId") channelId: String): Response<EmoteDtos.BTTV.Result>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("emotes/global")
    suspend fun getGlobalEmotes(): Response<List<EmoteDtos.BTTV.GlobalEmote>>
}