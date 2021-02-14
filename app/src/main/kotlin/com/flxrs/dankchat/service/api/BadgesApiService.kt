package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.BadgeDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Url

interface BadgesApiService {
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("global/display")
    suspend fun getGlobalBadges(): Response<BadgeDtos.Result>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("channels/{channelId}/display")
    suspend fun getChannelBadges(@Path("channelId") channelId: String): Response<BadgeDtos.Result>
}