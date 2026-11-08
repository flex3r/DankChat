package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.toUserNames
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.model.ChannelWithRename
import com.flxrs.dankchat.ui.changelog.DankChatVersion
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class DankChatPreferenceStore(
    private val context: Context,
    private val json: Json,
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore,
    private val authDataStore: AuthDataStore,
) {
    private val dankChatPreferences: SharedPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)

    private var channelRenames: String?
        get() = dankChatPreferences.getString(RENAME_KEY, null)
        set(value) = dankChatPreferences.edit { putString(RENAME_KEY, value) }

    val isLoggedIn: Boolean get() = authDataStore.isLoggedIn
    val oAuthKey: String? get() = authDataStore.oAuthKey
    val clientId: String get() = authDataStore.clientId

    var channels: List<UserName>
        get() =
            dankChatPreferences
                .getString(CHANNELS_AS_STRING_KEY, null)
                ?.split(',')
                .orEmpty()
                .toUserNames()
        set(value) {
            val channels =
                value
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = ",")
            dankChatPreferences.edit { putString(CHANNELS_AS_STRING_KEY, channels) }
        }

    val userName: UserName? get() = authDataStore.userName

    var displayName: DisplayName?
        get() = authDataStore.displayName
        set(value) {
            authDataStore.updateAsync { it.copy(displayName = value?.value) }
        }

    var userIdString: UserId?
        get() = authDataStore.userIdString
        set(value) {
            authDataStore.updateAsync { it.copy(userId = value?.value) }
        }

    var userId: Int
        get() = dankChatPreferences.getInt(ID_KEY, 0)
        set(value) = dankChatPreferences.edit { putInt(ID_KEY, value) }

    var hasExternalHostingAcknowledged: Boolean
        get() = dankChatPreferences.getBoolean(EXTERNAL_HOSTING_ACK_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(EXTERNAL_HOSTING_ACK_KEY, value) }

    var hasMessageHistoryAcknowledged: Boolean
        get() = dankChatPreferences.getBoolean(MESSAGES_HISTORY_ACK_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(MESSAGES_HISTORY_ACK_KEY, value) }

    var keyboardHeightPortrait: Int
        get() = dankChatPreferences.getInt(KEYBOARD_HEIGHT_PORTRAIT_KEY, 0)
        set(value) = dankChatPreferences.edit { putInt(KEYBOARD_HEIGHT_PORTRAIT_KEY, value) }

    var keyboardHeightLandscape: Int
        get() = dankChatPreferences.getInt(KEYBOARD_HEIGHT_LANDSCAPE_KEY, 0)
        set(value) = dankChatPreferences.edit { putInt(KEYBOARD_HEIGHT_LANDSCAPE_KEY, value) }

    var isSecretDankerModeEnabled: Boolean
        get() = dankChatPreferences.getBoolean(SECRET_DANKER_MODE_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(SECRET_DANKER_MODE_KEY, value) }

    val secretDankerModeClicks: Int = SECRET_DANKER_MODE_CLICKS

    val isLoggedInFlow: Flow<Boolean> =
        authDataStore.settings
            .map { it.isLoggedIn }
            .distinctUntilChanged()

    val currentUserAndDisplayFlow: Flow<Pair<UserName?, DisplayName?>> =
        authDataStore.settings
            .map { authDataStore.userName to authDataStore.displayName }
            .distinctUntilChanged()

    fun formatViewersString(
        viewers: Int,
        uptime: String,
        category: String?,
    ): String = when (category) {
        null -> context.resources.getQuantityString(R.plurals.viewers_and_uptime, viewers, viewers, uptime)
        else -> context.resources.getQuantityString(R.plurals.viewers_and_uptime_with_cateogry, viewers, viewers, category, uptime)
    }

    fun formatViewersStringCompact(
        viewers: Int,
        uptime: String,
        category: String?,
    ): String = when (category) {
        null -> context.getString(R.string.viewers_and_uptime_compact, viewers, uptime)
        else -> context.getString(R.string.viewers_and_uptime_with_category_compact, viewers, category, uptime)
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

        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
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

    // Written synchronously since a restart follows right after
    fun replaceChannels(channelsWithRenames: List<ChannelWithRename>) {
        val renames = channelsWithRenames.mapNotNull { (channel, rename) -> rename?.let { channel to it } }.toMap()
        val channelsString = channelsWithRenames.takeIf { it.isNotEmpty() }?.joinToString(separator = ",") { it.channel.value }
        dankChatPreferences.edit(commit = true) {
            putString(CHANNELS_AS_STRING_KEY, channelsString)
            putString(RENAME_KEY, renames.toJson())
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
        private const val ID_KEY = "idKey"
        private const val RENAME_KEY = "renameKey"
        private const val CHANNELS_AS_STRING_KEY = "channelsAsStringKey"
        private const val EXTERNAL_HOSTING_ACK_KEY = "nuulsAckKey" // the key is old key to prevent triggering the dialog for existing users
        private const val MESSAGES_HISTORY_ACK_KEY = "messageHistoryAckKey"
        private const val KEYBOARD_HEIGHT_PORTRAIT_KEY = "keyboardHeightPortraitKey"
        private const val KEYBOARD_HEIGHT_LANDSCAPE_KEY = "keyboardHeightLandscapeKey"
        private const val SECRET_DANKER_MODE_KEY = "secretDankerModeKey"
        private const val LAST_INSTALLED_VERSION_KEY = "lastInstalledVersionKey"

        private const val SECRET_DANKER_MODE_CLICKS = 5
    }
}
