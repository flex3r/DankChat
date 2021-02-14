package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.BadgeDtos
import com.flxrs.dankchat.service.api.dto.EmoteDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Url

interface DankChatApiService {
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("set/{setId}")
    suspend fun getSet(@Path("setId") setId: String): Response<List<EmoteDtos.Twitch.EmoteSet>>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("badges")
    suspend fun getDankChatBadges(): Response<List<BadgeDtos.DankChatBadge>>
}