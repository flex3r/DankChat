package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.SupibotDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface SupibotApiService {
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("bot/channel/list")
    suspend fun getChannels(@Query("platformName") platformName: String): Response<SupibotDtos.Channels>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("bot/command/list/")
    suspend fun getCommands(): Response<SupibotDtos.Commands>
}