package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.DankChatBadgeDto
import com.flxrs.dankchat.service.api.dto.DankChatEmoteSetDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface DankChatApiService {
    @GET("set/{setId}")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getSet(@Path("setId") setId: String): Response<List<DankChatEmoteSetDto>>

    @GET("sets")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getSets(@Query("id") ids: String): Response<List<DankChatEmoteSetDto>>

    @GET("badges")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getDankChatBadges(): Response<List<DankChatBadgeDto>>
}