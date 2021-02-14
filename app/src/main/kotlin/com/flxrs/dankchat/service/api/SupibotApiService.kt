package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.SupibotDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SupibotApiService {
    @GET("bot/channel/list")
    suspend fun getChannels(@Query("platformName") platformName: String): Response<SupibotDtos.Channels>

    @GET("bot/command/list/")
    suspend fun getCommands(): Response<SupibotDtos.Commands>
}