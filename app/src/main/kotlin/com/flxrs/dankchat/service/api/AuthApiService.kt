package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.UserDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers

interface AuthApiService {
    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET("validate")
    suspend fun validateUser(
        @Header("Authorization") oAuth: String
    ): Response<UserDtos.ValidateUser>
}