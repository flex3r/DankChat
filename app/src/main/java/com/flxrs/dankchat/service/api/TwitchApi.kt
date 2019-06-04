package com.flxrs.dankchat.service.api

import android.util.Log
import com.flxrs.dankchat.service.api.model.BadgeEntities
import com.flxrs.dankchat.service.api.model.EmoteEntities
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object TwitchApi {
	private val TAG = TwitchApi::class.java.simpleName
	private val scope = CoroutineScope(Dispatchers.IO + Job())

	private const val KRAKEN_BASE_URL = "https://api.twitch.tv/kraken/"
	private const val HELIX_BASE_URL = "https://api.twitch.tv/helix/"

	private const val TWITCH_SUBBADGES_BASE_URL = "https://badges.twitch.tv/v1/badges/channels/"
	private const val TWITCH_SUBBADGES_SUFFIX = "/display"
	private const val TWITCH_BADGES_URL = "https://badges.twitch.tv/v1/badges/global/display"

	private const val FFZ_BASE_URL = "https://api.frankerfacez.com/v1/room/"
	private const val FFZ_GLOBAL_URL = "https://api.frankerfacez.com/v1/set/global"

	private const val BASE_LOGIN_URL = "https://id.twitch.tv/oauth2/authorize?response_type=token"
	private const val REDIRECT_URL = "https://flxrs.com/dankchat"
	private const val SCOPES = "chat:edit+chat:read+user_read"
	const val CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
	const val LOGIN_URL = "$BASE_LOGIN_URL&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URL&scope=$SCOPES"

	private val service = Retrofit.Builder()
			.baseUrl(KRAKEN_BASE_URL)
			.addConverterFactory(MoshiConverterFactory.create())
			.addCallAdapterFactory(CoroutineCallAdapterFactory())
			.build()
			.create(TwitchService::class.java)

	suspend fun getUserName(oAuth: String): String = withContext(Dispatchers.IO) {
		try {
			service.getUserAsync("OAuth $oAuth").await().run {
				if (isSuccessful) return@withContext body()?.name ?: ""
			}
		} catch (e: Throwable) {
			Log.e(TAG, e.message)
		}
		return@withContext ""
	}

	suspend fun getChannelBadges(channel: String): BadgeEntities.BadgeSets? = withContext(Dispatchers.IO) {
		getUserIdFromName(channel)?.let {
			try {
				val response = service.getBadgeSetsAsync("$TWITCH_SUBBADGES_BASE_URL$it$TWITCH_SUBBADGES_SUFFIX").await()
				return@withContext if (response.isSuccessful) response.body() else null
			} catch (e: Throwable) {
				Log.e(TAG, e.message)
			}
		}
		return@withContext null
	}

	suspend fun getGlobalBadges(): BadgeEntities.BadgeSets? = withContext(Dispatchers.IO) {
		try {
			val response = service.getBadgeSetsAsync(TWITCH_BADGES_URL).await()
			if (response.isSuccessful) return@withContext response.body()
		} catch (e: Throwable) {
			Log.e(TAG, e.message)
		}
		return@withContext null
	}

	suspend fun getFFZChannelEmotes(channel: String): EmoteEntities.FFZ.Result? = withContext(Dispatchers.IO) {
		try {
			val response = service.getFFZChannelEmotesAsync("$FFZ_BASE_URL$channel").await()
			if (response.isSuccessful) return@withContext response.body()
		} catch (e: Throwable) {
			Log.e(TAG, e.message)
		}
		return@withContext null
	}

	suspend fun getFFZGlobalEmotes(): EmoteEntities.FFZ.GlobalResult? = withContext(Dispatchers.IO) {
		try {
			val response = service.getFFZGlobalEmotesAsync(FFZ_GLOBAL_URL).await()
			if (response.isSuccessful) return@withContext response.body()
			else Log.e(TAG, response.message())
		} catch (e: Throwable) {
			Log.e(TAG, e.message)
		}
		return@withContext null
	}

	private suspend fun getUserIdFromName(name: String): String? = withContext(Dispatchers.IO) {
		try {
			service.getUserHelixAsync("${HELIX_BASE_URL}users?login=$name").await().run {
				if (isSuccessful) return@withContext body()?.data?.get(0)?.id
			}
		} catch (e: Throwable) {
			Log.e(TAG, e.message)
		}
		return@withContext null
	}
}
