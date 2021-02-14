package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.UserDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query

interface HelixApiService {
    @Headers("Client-ID: ${ApiManager.CLIENT_ID}")
    @GET("users/")
    suspend fun getUser(
        @Header("Authorization") oAuth: String,
        @Query("login") login: String
    ): Response<UserDtos.HelixUsers>
}