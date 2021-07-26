package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.TwitchBadgesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface BadgesApiService {
    @GET("global/display")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getGlobalBadges(): Response<TwitchBadgesDto>

    @GET("channels/{channelId}/display")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getChannelBadges(@Path("channelId") channelId: String): Response<TwitchBadgesDto>
}