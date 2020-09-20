package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.flxrs.dankchat.R

class DankChatPreferenceStore(context: Context) {
    private val dankChatPreferences: SharedPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)
    // TODO wait until tink/jetpack security fixes https://issuetracker.google.com/issues/164901843 / https://issuetracker.google.com/issues/158234058#comment48
    //private val secureDankChatPreferences = EncryptedSharedPreferences.create(
    //    context,
    //    context.getString(R.string.secure_shared_preference_key),
    //    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    //    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    //    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    //)

    var isLoggedIn: Boolean
        get() = dankChatPreferences.getBoolean(LOGGED_IN_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(LOGGED_IN_KEY, value) }

    var oAuthKey: String?
        get() {
            //if (dankChatPreferences.contains(OAUTH_KEY)) {
            //    val oAuth = dankChatPreferences.getString(OAUTH_KEY, null)
            //    secureDankChatPreferences.edit { putString(OAUTH_KEY, oAuth) }
            //    dankChatPreferences.edit { remove(OAUTH_KEY) }
            //    return oAuth
            //}
            //return secureDankChatPreferences.getString(OAUTH_KEY, null)
            return dankChatPreferences.getString(OAUTH_KEY, null)
        }
        set(value) = dankChatPreferences.edit { putString(OAUTH_KEY, value) } //secureDankChatPreferences.edit { putString(OAUTH_KEY, value) }

    var channelsString: String?
        get() = dankChatPreferences.getString(CHANNELS_AS_STRING_KEY, null)
        set(value) = dankChatPreferences.edit { putString(CHANNELS_AS_STRING_KEY, value) }

    var channels: MutableSet<String>?
        get() = dankChatPreferences.getStringSet(CHANNELS_KEY, setOf())
        set(value) = dankChatPreferences.edit { putStringSet(CHANNELS_KEY, value) }

    var userName: String?
        get() = dankChatPreferences.getString(NAME_KEY, null)
        set(value) = dankChatPreferences.edit { putString(NAME_KEY, value) }

    var userId: Int
        get() = dankChatPreferences.getInt(ID_KEY, 0)
        set(value) = dankChatPreferences.edit { putInt(ID_KEY, value) }

    var userIdString: String?
        get() = dankChatPreferences.getString(ID_STRING_KEY, null)
        set(value) = dankChatPreferences.edit { putString(ID_STRING_KEY, value) }

    var hasNuulsAcknowledged: Boolean
        get() = dankChatPreferences.getBoolean(NUULS_ACK_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(NUULS_ACK_KEY, value) }

    var hasMessageHistoryAcknowledged: Boolean
        get() = dankChatPreferences.getBoolean(MESSAGES_HISTORY_ACK_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(MESSAGES_HISTORY_ACK_KEY, value) }

    var hasApiChangeAcknowledged: Boolean
        get() = dankChatPreferences.getBoolean(API_CHANGE_ACK_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(API_CHANGE_ACK_KEY, value) }

    fun clearLogin() = dankChatPreferences.edit {
        putBoolean(LOGGED_IN_KEY, false)
        putString(OAUTH_KEY, "")
        putString(NAME_KEY, "")
        putString(ID_STRING_KEY, "")
    }

    companion object {
        private const val LOGGED_IN_KEY = "loggedIn"
        private const val OAUTH_KEY = "oAuthKey"
        private const val NAME_KEY = "nameKey"
        private const val CHANNELS_KEY = "channelsKey"
        private const val CHANNELS_AS_STRING_KEY = "channelsAsStringKey"
        private const val ID_KEY = "idKey"
        private const val ID_STRING_KEY = "idStringKey"
        private const val NUULS_ACK_KEY = "nuulsAckKey"
        private const val MESSAGES_HISTORY_ACK_KEY = "messageHistoryAckKey"
        private const val API_CHANGE_ACK_KEY = "apiChangeAckKey"
    }
}