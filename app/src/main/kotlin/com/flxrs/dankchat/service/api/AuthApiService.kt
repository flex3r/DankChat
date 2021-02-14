package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.UserDtos
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface AuthApiService {
    @GET("validate")
    suspend fun validateUser(
        @Header("Authorization") oAuth: String
    ): Response<UserDtos.ValidateUser>
}