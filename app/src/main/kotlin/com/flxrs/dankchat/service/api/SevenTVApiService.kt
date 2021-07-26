package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.SevenTVEmoteDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface SevenTVApiService {
    @GET("users/{user}/emotes")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getChannelEmotes(@Path("user") channel: String): Response<List<SevenTVEmoteDto>>

    @GET("emotes/global")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getGlobalEmotes(): Response<List<SevenTVEmoteDto>>
}