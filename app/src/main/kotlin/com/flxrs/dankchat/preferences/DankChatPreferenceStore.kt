@file:Suppress("DEPRECATION")

package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.R
import com.flxrs.dankchat.changelog.DankChatVersion
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.toUserNames
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.model.ChannelWithRename
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class DankChatPreferenceStore(
    private val context: Context,
    private val json: Json,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
) {
    private val dankChatPreferences: SharedPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)

    private var channelRenames: String?
        get() = dankChatPreferences.getString(RENAME_KEY, null)
        set(value) = dankChatPreferences.edit { putString(RENAME_KEY, value) }

    var isLoggedIn: Boolean
        get() = dankChatPreferences.getBoolean(LOGGED_IN_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(LOGGED_IN_KEY, value) }

    var oAuthKey: String?
        get() = dankChatPreferences.getString(OAUTH_KEY, null)
        set(value) = dankChatPreferences.edit { putString(OAUTH_KEY, value) }

    var clientId: String
        get() = dankChatPreferences.getString(CLIENT_ID_KEY, null) ?: DEFAULT_CLIENT_ID
        set(value) = dankChatPreferences.edit { putString(CLIENT_ID_KEY, value) }

    var channels: List<UserName>
        get() = dankChatPreferences.getString(CHANNELS_AS_STRING_KEY, null)?.split(',').orEmpty().toUserNames()
        set(value) {
            val channels = value
                .takeIf { it.isNotEmpty() }
                ?.joinToString(separator = ",")
            dankChatPreferences.edit { putString(CHANNELS_AS_STRING_KEY, channels) }
        }

    var userName: UserName?
        get() = dankChatPreferences.getString(NAME_KEY, null)?.ifBlank { null }?.toUserName()
        set(value) = dankChatPreferences.edit { putString(NAME_KEY, value?.value?.ifBlank { null }) }

    var displayName: DisplayName?
        get() = dankChatPreferences.getString(DISPLAY_NAME_KEY, null)?.ifBlank { null }?.toDisplayName()
        set(value) = dankChatPreferences.edit { putString(DISPLAY_NAME_KEY, value?.value?.ifBlank { null }) }

    var userId: Int
        get() = dankChatPreferences.getInt(ID_KEY, 0)
        set(value) = dankChatPreferences.edit { putInt(ID_KEY, value) }

    var userIdString: UserId?
        get() = dankChatPreferences.getString(ID_STRING_KEY, null)?.ifBlank { null }?.toUserId()
        set(value) = dankChatPreferences.edit { putString(ID_STRING_KEY, value?.value?.ifBlank { null }) }

    var hasExternalHostingAcknowledged: Boolean
        get() = dankChatPreferences.getBoolean(EXTERNAL_HOSTING_ACK_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(EXTERNAL_HOSTING_ACK_KEY, value) }

    var hasMessageHistoryAcknowledged: Boolean
        get() = dankChatPreferences.getBoolean(MESSAGES_HISTORY_ACK_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(MESSAGES_HISTORY_ACK_KEY, value) }

    var isSecretDankerModeEnabled: Boolean
        get() = dankChatPreferences.getBoolean(SECRET_DANKER_MODE_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(SECRET_DANKER_MODE_KEY, value) }

    val secretDankerModeClicks: Int = SECRET_DANKER_MODE_CLICKS

    val currentUserAndDisplayFlow: Flow<Pair<UserName?, DisplayName?>> = callbackFlow {
        send(userName to displayName)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == NAME_KEY || key == DISPLAY_NAME_KEY) {
                trySend(userName to displayName)
            }
        }

        dankChatPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { dankChatPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun formatViewersString(viewers: Int, uptime: String): String = context.resources.getQuantityString(R.plurals.viewers_and_uptime, viewers, viewers, uptime)

    fun clearLogin() = dankChatPreferences.edit {
        putBoolean(LOGGED_IN_KEY, false)
        putString(OAUTH_KEY, null)
        putString(NAME_KEY, null)
        putString(ID_STRING_KEY, null)
        putString(CLIENT_ID_KEY, null)
    }

    fun removeChannel(channel: UserName): List<UserName> {
        val updated = channels - channel
        dankChatPreferences.edit {
            removeChannelRename(channel)
            channels = updated
        }
        return updated
    }

    fun getChannelsWithRenames(channels: List<UserName> = this.channels): List<ChannelWithRename> {
        val renameMap = channelRenames?.toMutableMap().orEmpty()
        return channels.map {
            ChannelWithRename(
                channel = it,
                rename = renameMap[it],
            )
        }
    }

    fun getChannelsWithRenamesFlow(): Flow<List<ChannelWithRename>> = callbackFlow {
        send(getChannelsWithRenames())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == RENAME_KEY || key == CHANNELS_AS_STRING_KEY) {
                trySend(getChannelsWithRenames())
            }
        }

        dankChatPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { dankChatPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setRenamedChannel(channelWithRename: ChannelWithRename) {
        withChannelRenames {
            when (channelWithRename.rename) {
                null -> remove(channelWithRename.channel)
                else -> put(channelWithRename.channel, channelWithRename.rename)
            }
        }
    }

    fun shouldShowChangelog(): Boolean {
        if (!appearanceSettingsDataStore.current().showChangelogs) {
            setCurrentInstalledVersionCode()
            return false
        }

        val latestChangelog = DankChatVersion.LATEST_CHANGELOG?.version ?: return false
        val lastViewed = lastViewedChangelogVersion?.let(DankChatVersion.Companion::fromString) ?: return true

        return lastViewed < latestChangelog
    }

    fun setCurrentInstalledVersionCode() {
        lastViewedChangelogVersion = BuildConfig.VERSION_NAME
    }

    private fun removeChannelRename(channel: UserName) {
        withChannelRenames {
            remove(channel)
        }
    }

    private inline fun withChannelRenames(block: MutableMap<UserName, UserName>.() -> Unit) {
        val renameMap = channelRenames?.toMutableMap() ?: mutableMapOf()
        renameMap.block()
        channelRenames = renameMap.toJson()
    }

    private fun String.toMutableMap(): MutableMap<UserName, UserName> = json
        .decodeOrNull<Map<UserName, UserName>>(this)
        .orEmpty()
        .toMutableMap()

    private fun Map<UserName, UserName>.toJson(): String = json.encodeToString(this)

    private var lastViewedChangelogVersion: String?
        get() = dankChatPreferences.getString(LAST_INSTALLED_VERSION_KEY, null)
        set(value) = dankChatPreferences.edit { putString(LAST_INSTALLED_VERSION_KEY, value) }

    companion object {
        private const val LOGGED_IN_KEY = "loggedIn"
        private const val OAUTH_KEY = "oAuthKey"
        private const val CLIENT_ID_KEY = "clientIdKey"
        private const val NAME_KEY = "nameKey"
        private const val DISPLAY_NAME_KEY = "displayNameKey"
        private const val RENAME_KEY = "renameKey"
        private const val CHANNELS_AS_STRING_KEY = "channelsAsStringKey"
        private const val ID_KEY = "idKey"
        private const val ID_STRING_KEY = "idStringKey"
        private const val EXTERNAL_HOSTING_ACK_KEY = "nuulsAckKey" // the key is old key to prevent triggering the dialog for existing users
        private const val MESSAGES_HISTORY_ACK_KEY = "messageHistoryAckKey"
        private const val SECRET_DANKER_MODE_KEY = "secretDankerModeKey"
        private const val LAST_INSTALLED_VERSION_KEY = "lastInstalledVersionKey"

        private const val SECRET_DANKER_MODE_CLICKS = 5

        const val DEFAULT_CLIENT_ID = "xu7vd1i6tlr0ak45q1li2wdc0lrma8"
    }
}
