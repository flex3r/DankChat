package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.ChattersDto
import com.flxrs.dankchat.service.api.dto.ChattersResultDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface TmiApiService {
    @GET("group/user/{channel}/chatters")
    suspend fun getChatters(@Path("channel") channel: String): Response<ChattersResultDto>
}