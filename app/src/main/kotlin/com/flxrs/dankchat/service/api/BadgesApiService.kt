package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.BadgeDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface BadgesApiService {
    @GET("global/display")
    suspend fun getGlobalBadges(): Response<BadgeDtos.Result>

    @GET("channels/{channelId}/display")
    suspend fun getChannelBadges(@Path("channelId") channelId: String): Response<BadgeDtos.Result>
}