package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.EmoteDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface FFZApiService {
    @GET("room/id/{channelId}")
    suspend fun getChannelEmotes(@Path("channelId") channelId: String): Response<EmoteDtos.FFZ.Result>

    @GET("set/global")
    suspend fun getGlobalEmotes(): Response<EmoteDtos.FFZ.GlobalResult>
}