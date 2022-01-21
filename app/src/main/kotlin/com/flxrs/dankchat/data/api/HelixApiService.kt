package com.flxrs.dankchat.data.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface HelixApiService {
    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/")
    suspend fun getUserByName(
        @Header("Authorization") oAuth: String,
        @Query("login") login: String
    ): Response<HelixUsersDto>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/")
    suspend fun getUserById(
        @Header("Authorization") oAuth: String,
        @Query("id") userId: String
    ): Response<HelixUsersDto>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/follows")
    suspend fun getUsersFollows(
        @Header("Authorization") oAuth: String,
        @Query("from_id") fromId: String,
        @Query("to_id") toId: String,
    ): Response<UserFollowsDto>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("streams/")
    suspend fun getStreams(
        @Header("Authorization") oAuth: String,
        @Query("user_login") channels: List<String>
    ): Response<StreamsDto>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/blocks/")
    suspend fun getUserBlocks(
        @Header("Authorization") oAuth: String,
        @Query("broadcaster_id") userId: String,
        @Query("first") first: Int = 100
    ): Response<HelixUserBlockListDto>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @PUT("users/blocks/")
    suspend fun putUserBlock(
        @Header("Authorization") oAuth: String,
        @Query("target_user_id") targetUserId: String
    ): Response<Unit>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @DELETE("users/blocks/")
    suspend fun deleteUserBlock(
        @Header("Authorization") oAuth: String,
        @Query("target_user_id") targetUserId: String
    ): Response<Unit>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("chat/badges")
    suspend fun getChannelBadges(
        @Header("Authorization") oAuth: String,
        @Query("broadcaster_id") userId: String
    ): Response<HelixBadgesDto>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("chat/badges/global")
    suspend fun getGlobalBadges(
        @Header("Authorization") oAuth: String
    ): Response<HelixBadgesDto>

    @Headers(
        "Client-ID: ${ApiManager.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("chat/emotes/set")
    suspend fun getEmoteSets(
        @Header("Authorization") oAuth: String,
        @Query("emote_set_id") setIds: List<String>
    ): Response<HelixEmoteSetsDto>
}