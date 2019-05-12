package com.flxrs.dankchat.utils

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TwitchApi {
	private val scope = CoroutineScope(Dispatchers.IO + Job())

	const val CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
	private const val BASE_LOGIN_URL = "https://id.twitch.tv/oauth2/authorize?response_type=token"
	private const val KRAKEN_USER_URL = "https://api.twitch.tv/kraken/user"
	private const val REDIRECT_URL = "https://flxrs.com/dankchat"
	private const val SCOPES = "chat:edit+chat:read+user_read"
	const val LOGIN_URL = "$BASE_LOGIN_URL&client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URL&scope=$SCOPES"

	fun getUserDataAsync(oAuth: String): Deferred<String> = scope.async {
		val url = URL(KRAKEN_USER_URL)
		val connection = url.openConnection() as HttpURLConnection
		connection.setRequestProperty("Accept", "application/vnd.twitchtv.v5+json")
		connection.setRequestProperty("Client-ID", CLIENT_ID)
		connection.setRequestProperty("Authorization", "OAuth $oAuth")
		val response = connection.inputStream.bufferedReader().readText()
		return@async JSONObject(response).optString("name")
	}

}
