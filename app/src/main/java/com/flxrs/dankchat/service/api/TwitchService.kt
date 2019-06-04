package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.model.BadgeEntities
import com.flxrs.dankchat.service.api.model.EmoteEntities
import com.flxrs.dankchat.service.api.model.RecentMessages
import com.flxrs.dankchat.service.api.model.UserEntities
import kotlinx.coroutines.Deferred
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
	fun getUserAsync(@Header("Authorization") oauth: String): Deferred<Response<UserEntities.FromKraken>>

	@Headers("Client-ID: ${TwitchApi.CLIENT_ID}")
	@GET
	fun getUserHelixAsync(@Url url: String): Deferred<Response<UserEntities.FromHelixAsArray>>

	@GET
	fun getBadgeSetsAsync(@Url url: String): Deferred<Response<BadgeEntities.BadgeSets>>

	@GET
	fun getFFZChannelEmotesAsync(@Url url: String): Deferred<Response<EmoteEntities.FFZ.Result>>

	@GET
	fun getFFZGlobalEmotesAsync(@Url url: String): Deferred<Response<EmoteEntities.FFZ.GlobalResult>>

	@GET
	fun getBTTVChannelEmotesAsync(@Url url: String): Deferred<Response<EmoteEntities.BTTV.Result>>

	@GET
	fun getBTTVGlobalEmotesAsync(@Url url: String): Deferred<Response<EmoteEntities.BTTV.GlobalResult>>

	@GET
	fun getRecentMessages(@Url url: String): Deferred<Response<RecentMessages>>
}