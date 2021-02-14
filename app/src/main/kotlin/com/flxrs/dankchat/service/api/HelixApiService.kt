package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface HelixApiService {
    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/")
    suspend fun getUser(
        @Header("Authorization") oAuth: String,
        @Query("login") login: String
    ): Response<UserDtos.HelixUsers>
}