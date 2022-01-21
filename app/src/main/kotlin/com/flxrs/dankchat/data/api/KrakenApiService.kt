package com.flxrs.dankchat.data.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.dto.TwitchEmotesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path

interface KrakenApiService {
    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/{id}/emotes")
    suspend fun getUserEmotes(
        @Header("Authorization") oauth: String,
        @Path("id") userId: String
    ): Response<TwitchEmotesDto>
}