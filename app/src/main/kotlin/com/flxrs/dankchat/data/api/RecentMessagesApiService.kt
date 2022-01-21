package com.flxrs.dankchat.data.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.dto.RecentMessagesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface RecentMessagesApiService {
    @GET("recent-messages/{channel}")
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    suspend fun getRecentMessages(@Path("channel") channel: String): Response<RecentMessagesDto>
}