package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.dto.UserDtos
import com.flxrs.dankchat.service.api.dto.UserFollowRequestBody
import com.flxrs.dankchat.service.api.dto.UserFollowsDto
import retrofit2.Response
import retrofit2.http.*

interface HelixApiService {
    @Headers("Client-ID: ${ApiManager.CLIENT_ID}")
    @GET("users/")
    suspend fun getUserByName(
        @Header("Authorization") oAuth: String,
        @Query("login") login: String
    ): Response<UserDtos.HelixUsers>

    @Headers("Client-ID: ${ApiManager.CLIENT_ID}")
    @GET("users/")
    suspend fun getUserById(
        @Header("Authorization") oAuth: String,
        @Query("id") userId: String
    ): Response<UserDtos.HelixUsers>

    @Headers("Client-ID: ${ApiManager.CLIENT_ID}")
    @GET("users/follows")
    suspend fun getUsersFollows(
        @Header("Authorization") oAuth: String,
        @Query("from_id") fromId: String,
        @Query("to_id") toId: String,
    ): Response<UserFollowsDto>

    @Headers("Client-ID: ${ApiManager.CLIENT_ID}")
    @POST("users/follows")
    suspend fun createUserFollows(
        @Header("Authorization") oAuth: String,
        @Body body: UserFollowRequestBody
    ): Response<Unit>

    @Headers("Client-ID: ${ApiManager.CLIENT_ID}")
    @DELETE("users/follows")
    suspend fun deleteUserFollows(
        @Header("Authorization") oAuth: String,
        @Query("from_id") fromId: String,
        @Query("to_id") toId: String,
    ): Response<Unit>
}