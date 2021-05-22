package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.RecentMessagesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface RecentMessagesApiService {
    @GET("recent-messages/{channel}")
    suspend fun getRecentMessages(@Path("channel") channel: String): Response<RecentMessagesDto>
}