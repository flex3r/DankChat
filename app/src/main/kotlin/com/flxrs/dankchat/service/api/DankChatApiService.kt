package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.DankChatBadgeDto
import com.flxrs.dankchat.service.api.dto.TwitchEmoteSetDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface DankChatApiService {
    @GET("set/{setId}")
    suspend fun getSet(@Path("setId") setId: String): Response<List<TwitchEmoteSetDto>>

    @GET("badges")
    suspend fun getDankChatBadges(): Response<List<DankChatBadgeDto>>
}