package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import com.flxrs.dankchat.R

class TwitchAuthStore(private val context: Context) {


	private fun getTwitchAuthSharedPreferences(): SharedPreferences {
		return context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)
	}

	fun isLoggedin(): Boolean {
		return getTwitchAuthSharedPreferences().getBoolean(LOGGED_IN_KEY, false)
	}

	fun setLoggedIn(boolean: Boolean) {
		getTwitchAuthSharedPreferences().edit().putBoolean(LOGGED_IN_KEY, boolean).apply()
	}

	fun getOAuthKey(): String? {
		return getTwitchAuthSharedPreferences().getString(OAUTH_KEY, null)
	}

	fun setOAuthKey(oauth: String) {
		getTwitchAuthSharedPreferences().edit().putString(OAUTH_KEY, oauth).apply()
	}

	fun getChannels(): MutableSet<String>? {
		return getTwitchAuthSharedPreferences().getStringSet(CHANNELS_KEY, setOf())
	}

	fun setChannels(channels: MutableSet<String>) {
		getTwitchAuthSharedPreferences().edit().putStringSet(CHANNELS_KEY, channels).apply()
	}

	fun setUserName(name: String) {
		getTwitchAuthSharedPreferences().edit().putString(NAME_KEY, name).apply()
	}

	fun getUserName(): String? {
		return getTwitchAuthSharedPreferences().getString(NAME_KEY, null)
	}

	companion object {
		private const val LOGGED_IN_KEY = "loggedIn"
		private const val OAUTH_KEY = "oAuthKey"
		private const val NAME_KEY = "nameKey"
		private const val CHANNELS_KEY = "channelsKey"
	}
}