package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.SevenTVEmoteDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface SevenTVApiService {
    @GET("users/{user}/emotes")
    suspend fun getChannelEmotes(@Path("user") channel: String): Response<List<SevenTVEmoteDto>>

    @GET("emotes/global")
    suspend fun getGlobalEmotes(): Response<List<SevenTVEmoteDto>>
}