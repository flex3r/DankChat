package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.ChatterCountDto
import com.flxrs.dankchat.service.api.dto.ChattersResultDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface TmiApiService {
    @GET("group/user/{channel}/chatters")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getChatters(@Path("channel") channel: String): Response<ChattersResultDto>

    @GET("group/user/{channel}/chatters")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getChatterCount(@Path("channel") channel: String): Response<ChatterCountDto>
}