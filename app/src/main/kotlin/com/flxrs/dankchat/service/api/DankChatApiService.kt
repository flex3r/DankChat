package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.BadgeDtos
import com.flxrs.dankchat.service.api.dto.EmoteDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface DankChatApiService {
    @GET("set/{setId}")
    suspend fun getSet(@Path("setId") setId: String): Response<List<EmoteDtos.Twitch.EmoteSet>>

    @GET("badges")
    suspend fun getDankChatBadges(): Response<List<BadgeDtos.DankChatBadge>>
}