package com.flxrs.dankchat.data.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.dto.SupibotChannelsDto
import com.flxrs.dankchat.data.api.dto.SupibotCommandsDto
import com.flxrs.dankchat.data.api.dto.SupibotUserAliasesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface SupibotApiService {
    @GET("bot/channel/list")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getChannels(@Query("platformName") platformName: String): Response<SupibotChannelsDto>

    @GET("bot/command/list/")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getCommands(): Response<SupibotCommandsDto>

    @GET("bot/user/{user}/alias/list/")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getUserAliases(@Path("user") user: String): Response<SupibotUserAliasesDto>
}