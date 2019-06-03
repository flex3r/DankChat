package com.flxrs.dankchat.service.api

import com.flxrs.dankchat.service.api.model.BadgeEntity
import com.flxrs.dankchat.service.api.model.UserEntity
import com.flxrs.dankchat.utils.TwitchApi
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
	fun getUserAsync(@Header("Authorization") oauth: String): Deferred<Response<UserEntity.FromKraken>>

	@Headers("Client-ID: ${TwitchApi.CLIENT_ID}")
	@GET
	fun getUserHelixAsync(@Url url: String): Deferred<Response<UserEntity.FromHelixAsArray>>

	@GET
	fun getChannelBadges(@Url url: String): Deferred<Response<BadgeEntity.BadgeSets>>
}