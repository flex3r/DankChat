package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.model.BadgeEntities
import com.flxrs.dankchat.service.api.model.EmoteEntities
import com.flxrs.dankchat.service.api.model.RecentMessages
import com.flxrs.dankchat.service.api.model.UserEntities
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Url

interface TwitchService {


	@Headers(
			"Accept: application/vnd.twitchtv.v5+json",
			"Client-ID: ${TwitchApi.CLIENT_ID}"
	)
	@GET("user")
	suspend fun getUser(@Header("Authorization") oauth: String): Response<UserEntities.FromKraken>

	@Headers("Client-ID: ${TwitchApi.CLIENT_ID}")
	@GET
	suspend fun getUserHelix(@Url url: String): Response<UserEntities.FromHelixAsArray>

	@GET
	suspend fun getBadgeSets(@Url url: String): Response<BadgeEntities.Result>

	@GET
	suspend fun getFFZChannelEmotes(@Url url: String): Response<EmoteEntities.FFZ.Result>

	@GET
	suspend fun getFFZGlobalEmotes(@Url url: String): Response<EmoteEntities.FFZ.GlobalResult>

	@GET
	suspend fun getBTTVChannelEmotes(@Url url: String): Response<EmoteEntities.BTTV.Result>

	@GET
	suspend fun getBTTVGlobalEmotes(@Url url: String): Response<EmoteEntities.BTTV.GlobalResult>

	@GET
	suspend fun getRecentMessages(@Url url: String): Response<RecentMessages>
}