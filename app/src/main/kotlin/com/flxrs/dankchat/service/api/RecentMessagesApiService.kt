package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.RecentMessagesDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Url

interface RecentMessagesApiService {
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("recent-messages/{channel}")
    suspend fun getRecentMessages(@Path("channel") channel: String): Response<RecentMessagesDto>
}