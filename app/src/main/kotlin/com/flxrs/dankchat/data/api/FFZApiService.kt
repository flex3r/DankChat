package com.flxrs.dankchat.data.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.dto.FFZChannelDto
import com.flxrs.dankchat.data.api.dto.FFZGlobalDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface FFZApiService {
    @GET("room/id/{channelId}")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getChannelEmotes(@Path("channelId") channelId: String): Response<FFZChannelDto>

    @GET("set/global")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getGlobalEmotes(): Response<FFZGlobalDto>
}