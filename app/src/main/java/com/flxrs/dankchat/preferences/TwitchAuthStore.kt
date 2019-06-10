package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.flxrs.dankchat.R

class TwitchAuthStore(private val context: Context) {


	private fun getTwitchAuthSharedPreferences(): SharedPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)

	fun isLoggedin(): Boolean = getTwitchAuthSharedPreferences().getBoolean(LOGGED_IN_KEY, false)

	fun setLoggedIn(boolean: Boolean) = getTwitchAuthSharedPreferences().edit { putBoolean(LOGGED_IN_KEY, boolean) }

	fun getOAuthKey(): String? = getTwitchAuthSharedPreferences().getString(OAUTH_KEY, null)

	fun setOAuthKey(oauth: String) = getTwitchAuthSharedPreferences().edit { putString(OAUTH_KEY, oauth) }

	fun getChannels(): MutableSet<String>? = getTwitchAuthSharedPreferences().getStringSet(CHANNELS_KEY, setOf())

	fun setChannels(channels: MutableSet<String>) = getTwitchAuthSharedPreferences().edit { putStringSet(CHANNELS_KEY, channels) }

	fun getUserName(): String? = getTwitchAuthSharedPreferences().getString(NAME_KEY, null)

	fun setUserName(name: String) = getTwitchAuthSharedPreferences().edit { putString(NAME_KEY, name) }

	fun getUserId(): Int = getTwitchAuthSharedPreferences().getInt(ID_KEY, 0)

	fun setUserId(id: Int) = getTwitchAuthSharedPreferences().edit { putInt(ID_KEY, id) }

	companion object {
		private const val LOGGED_IN_KEY = "loggedIn"
		private const val OAUTH_KEY = "oAuthKey"
		private const val NAME_KEY = "nameKey"
		private const val CHANNELS_KEY = "channelsKey"
		private const val ID_KEY = "idKey"
	}
}