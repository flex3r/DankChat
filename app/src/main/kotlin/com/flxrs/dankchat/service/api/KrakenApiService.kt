package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.TwitchEmotesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path

interface KrakenApiService {
    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${ApiManager.CLIENT_ID}"
    )
    @GET("users/{id}/emotes")
    suspend fun getUserEmotes(
        @Header("Authorization") oauth: String,
        @Path("id") userId: String
    ): Response<TwitchEmotesDto>
}