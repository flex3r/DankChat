package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface TwitchApiService {


    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun validateUser(
        @Url url: String,
        @Header("Authorization") oAuth: String
    ): Response<UserDtos.ValidateUser>

    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/{id}/emotes")
    suspend fun getUserEmotes(
        @Header("Authorization") oauth: String,
        @Path("id") userId: String
    ): Response<EmoteDtos.Twitch.Result>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getSets(@Url url: String): Response<List<EmoteDtos.Twitch.EmoteSet>>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getSet(@Url url: String): Response<List<EmoteDtos.Twitch.EmoteSet>>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getDankChatBadges(@Url url: String): Response<List<BadgeDtos.DankChatBadge>>

    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("streams/{id}")
    suspend fun getStream(@Path("id") channelId: Int): Response<StreamDtos.Result>

    @Headers(
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET
    suspend fun getUserHelix(
        @Header("Authorization") oAuth: String,
        @Url url: String
    ): Response<UserDtos.HelixUsers>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getBadgeSets(@Url url: String): Response<BadgeDtos.Result>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getFFZChannelEmotes(@Url url: String): Response<EmoteDtos.FFZ.Result>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getFFZGlobalEmotes(@Url url: String): Response<EmoteDtos.FFZ.GlobalResult>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getBTTVChannelEmotes(@Url url: String): Response<EmoteDtos.BTTV.Result>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getBTTVGlobalEmotes(@Url url: String): Response<List<EmoteDtos.BTTV.GlobalEmote>>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getRecentMessages(@Url url: String): Response<RecentMessagesDto>

    @Headers(
        "Accept: application/vnd.twitchtv.v5+json",
        "Client-ID: ${TwitchApi.CLIENT_ID}",
        "User-Agent: dankchat/${BuildConfig.VERSION_NAME}"
    )
    @GET("users/{id}/blocks")
    suspend fun getIgnores(
        @Header("Authorization") oauth: String,
        @Path("id") userId: String
    ): Response<UserDtos.KrakenUsersBlocks>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getSupibotChannels(@Url url: String, @Query("platformName") platform: String): Response<SupibotDtos.Channels>

    @Headers("User-Agent: dankchat/${BuildConfig.VERSION_NAME}")
    @GET
    suspend fun getSupibotCommands(@Url url: String): Response<SupibotDtos.Commands>
}