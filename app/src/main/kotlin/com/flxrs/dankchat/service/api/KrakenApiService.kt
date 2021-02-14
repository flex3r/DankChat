package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.EmoteDtos
import com.flxrs.dankchat.service.api.dto.StreamDtos
import com.flxrs.dankchat.service.api.dto.UserDtos
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
    ): Response<EmoteDtos.Twitch.Result>

    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${ApiManager.CLIENT_ID}"
    )
    @GET("streams/{id}")
    suspend fun getStream(@Path("id") channelId: Int): Response<StreamDtos.Result>

    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${ApiManager.CLIENT_ID}"
    )
    @GET("users/{id}/blocks")
    suspend fun getIgnores(
        @Header("Authorization") oauth: String,
        @Path("id") userId: String
    ): Response<UserDtos.KrakenUsersBlocks>
}