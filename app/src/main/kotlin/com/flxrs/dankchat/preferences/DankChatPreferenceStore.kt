package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.flxrs.dankchat.R
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject

class DankChatPreferenceStore @Inject constructor(context: Context) {
    private val dankChatPreferences: SharedPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)
    private val adapterType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    private val moshiAdapter = Moshi.Builder().build().adapter<Map<String, String>>(adapterType)

    private var channelRenames: String?
        get() = dankChatPreferences.getString(RENAME_KEY, null)
        set(value) = dankChatPreferences.edit { putString(RENAME_KEY, value) }

    var isLoggedIn: Boolean
        get() = dankChatPreferences.getBoolean(LOGGED_IN_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(LOGGED_IN_KEY, value) }

    var oAuthKey: String?
        get() = dankChatPreferences.getString(OAUTH_KEY, null)
        set(value) = dankChatPreferences.edit { putString(OAUTH_KEY, value) }

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

    fun clearLogin() = dankChatPreferences.edit {
        putBoolean(LOGGED_IN_KEY, false)
        putString(OAUTH_KEY, "")
        putString(NAME_KEY, "")
        putString(ID_STRING_KEY, "")
    }

    fun getChannels(): List<String> = channelsString?.split(',') ?: channels.also { channels = null }?.toList().orEmpty()

    fun getRenamedChannel(channel: String): String? {
        val renameMap = channelRenames?.toMutableMap()
        return renameMap?.get(channel)
    }

    fun setRenamedChannel(channel: String, renaming: String) {
        withChannelRenames {
            put(channel, renaming)
        }
    }

    fun removeChannelRename(channel: String) {
        withChannelRenames {
            remove(channel)
        }
    }

    private fun withChannelRenames(block: MutableMap<String, String>.() -> Unit) {
        val renameMap = channelRenames?.toMutableMap() ?: mutableMapOf()
        renameMap.block()
        channelRenames = renameMap.toJson()
    }

    private fun String.toMutableMap(): MutableMap<String, String> {
        return kotlin.runCatching {
            moshiAdapter.fromJson(this) as MutableMap<String, String>
        }.getOrElse {
            mutableMapOf()
        }
    }

    private fun Map<String,String>.toJson(): String = moshiAdapter.toJson(this)

    companion object {
        private const val LOGGED_IN_KEY = "loggedIn"
        private const val OAUTH_KEY = "oAuthKey"
        private const val NAME_KEY = "nameKey"
        private const val CHANNELS_KEY = "channelsKey"
        private const val RENAME_KEY = "renameKey"
        private const val CHANNELS_AS_STRING_KEY = "channelsAsStringKey"
        private const val ID_KEY = "idKey"
        private const val ID_STRING_KEY = "idStringKey"
        private const val NUULS_ACK_KEY = "nuulsAckKey"
        private const val MESSAGES_HISTORY_ACK_KEY = "messageHistoryAckKey"
    }
}