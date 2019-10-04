package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.model.*
import retrofit2.Response
import retrofit2.http.*

interface TwitchApiService {


    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("user")
    suspend fun getUser(@Header("Authorization") oauth: String): Response<UserEntities.KrakenUser>

    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/{id}/emotes")
    suspend fun getUserEmotes(@Header("Authorization") oauth: String, @Path("id") userId: Int): Response<EmoteEntities.Twitch.Result>

    @Headers(
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getSets(@Url url: String): Response<List<EmoteEntities.Twitch.EmoteSet>>

    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("streams/{id}")
    suspend fun getStream(@Path("id") channelId: Int): Response<StreamEntities.Result>

    @Headers(
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getUserHelix(@Url url: String): Response<UserEntities.HelixUsers>

    @Headers(
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getBadgeSets(@Url url: String): Response<BadgeEntities.Result>

    @Headers(
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getFFZChannelEmotes(@Url url: String): Response<EmoteEntities.FFZ.Result>

    @Headers(
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getFFZGlobalEmotes(@Url url: String): Response<EmoteEntities.FFZ.GlobalResult>

    @Headers(
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getBTTVChannelEmotes(@Url url: String): Response<EmoteEntities.BTTV.Result>

    @Headers(
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getBTTVGlobalEmotes(@Url url: String): Response<List<EmoteEntities.BTTV.GlobalEmote>>

    @Headers(
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getRecentMessages(@Url url: String): Response<RecentMessages>

    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/{id}/blocks")
    suspend fun getIgnores(@Header("Authorization") oauth: String, @Path("id") userId: Int): Response<UserEntities.KrakenUsersBlocks>
}