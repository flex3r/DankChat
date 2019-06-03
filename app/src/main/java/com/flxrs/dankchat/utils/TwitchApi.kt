package com.flxrs.dankchat.utils

import android.util.Log
import com.flxrs.dankchat.service.api.TwitchService
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

	suspend fun getUserIdFromName(name: String): String = withContext(Dispatchers.IO) {
		try {
			service.getUserHelixAsync("${HELIX_BASE_URL}users?login=$name").await().run {
				if (isSuccessful) return@withContext body()?.data?.get(0)?.id ?: ""
			}
		} catch (e: Throwable) {
			Log.e(TAG, e.message)
		}
		return@withContext ""
	}
}
