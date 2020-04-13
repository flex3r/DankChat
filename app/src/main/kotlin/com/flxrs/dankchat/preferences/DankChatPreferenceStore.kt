package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.flxrs.dankchat.R

class DankChatPreferenceStore(context: Context) {


    private val dankChatPreferences: SharedPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)

    fun isLoggedin(): Boolean = dankChatPreferences.getBoolean(LOGGED_IN_KEY, false)

    fun setLoggedIn(boolean: Boolean) = dankChatPreferences.edit { putBoolean(LOGGED_IN_KEY, boolean) }

    fun getOAuthKey(): String? = dankChatPreferences.getString(OAUTH_KEY, null)

    fun setOAuthKey(oauth: String) = dankChatPreferences.edit { putString(OAUTH_KEY, oauth) }

    fun getChannelsAsString(): String? = dankChatPreferences.getString(CHANNELS_AS_STRING_KEY, null)

    fun setChannelsString(channels: String?) = dankChatPreferences.edit { putString(CHANNELS_AS_STRING_KEY, channels) }

    fun getChannels(): MutableSet<String>? = dankChatPreferences.getStringSet(CHANNELS_KEY, setOf())

    fun setChannels(channels: MutableSet<String>?) = dankChatPreferences.edit { putStringSet(CHANNELS_KEY, channels) }

    fun getUserName(): String? = dankChatPreferences.getString(NAME_KEY, null)

    fun setUserName(name: String) = dankChatPreferences.edit { putString(NAME_KEY, name) }

    fun getUserId(): Int = dankChatPreferences.getInt(ID_KEY, 0)

    fun setUserId(id: Int) = dankChatPreferences.edit { putInt(ID_KEY, id) }

    fun getNuulsAcknowledge() = dankChatPreferences.getBoolean(NUULS_ACK_KEY, false)

    fun setNuulsAcknowledge(ack: Boolean) = dankChatPreferences.edit { putBoolean(NUULS_ACK_KEY, ack) }

    companion object {
        private const val LOGGED_IN_KEY = "loggedIn"
        private const val OAUTH_KEY = "oAuthKey"
        private const val NAME_KEY = "nameKey"
        private const val CHANNELS_KEY = "channelsKey"
        private const val CHANNELS_AS_STRING_KEY = "channelsAsStringKey"
        private const val ID_KEY = "idKey"
        private const val NUULS_ACK_KEY = "nuulsAckKey"
    }
}