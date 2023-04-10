@file:Suppress("DEPRECATION")

package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.R
import com.flxrs.dankchat.changelog.DankChatVersion
import com.flxrs.dankchat.data.*
import com.flxrs.dankchat.data.twitch.badge.BadgeType
import com.flxrs.dankchat.data.twitch.emote.ThirdPartyEmoteType
import com.flxrs.dankchat.preferences.command.CommandDto
import com.flxrs.dankchat.preferences.command.CommandDto.Companion.toEntryItem
import com.flxrs.dankchat.preferences.command.CommandItem
import com.flxrs.dankchat.preferences.multientry.MultiEntryDto
import com.flxrs.dankchat.preferences.upload.ImageUploader
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DankChatPreferenceStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val dankChatPreferences: SharedPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)
    private val defaultPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private var channelRenames: String?
        get() = dankChatPreferences.getString(RENAME_KEY, null)
        set(value) = dankChatPreferences.edit { putString(RENAME_KEY, value) }

    var isLoggedIn: Boolean
        get() = dankChatPreferences.getBoolean(LOGGED_IN_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(LOGGED_IN_KEY, value) }

    var oAuthKey: String?
        get() = dankChatPreferences.getString(OAUTH_KEY, null)
        set(value) = dankChatPreferences.edit { putString(OAUTH_KEY, value) }

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

    var customImageUploader: ImageUploader
        get() {
            val url = dankChatPreferences.getString(UPLOADER_URL, null) ?: return DEFAULT_UPLOADER

            val formField = dankChatPreferences.getString(UPLOADER_FORM_FIELD, UPLOADER_FORM_FIELD_DEFAULT) ?: UPLOADER_FORM_FIELD_DEFAULT
            val headers = dankChatPreferences.getString(UPLOADER_HEADERS, null)
            val imageLinkPattern = dankChatPreferences.getString(UPLOADER_IMAGE_LINK, null)
            val deletionLinkPattern = dankChatPreferences.getString(UPLOADER_DELETION_LINK, null)

            return ImageUploader(
                uploadUrl = url,
                formField = formField,
                headers = headers,
                imageLinkPattern = imageLinkPattern,
                deletionLinkPattern = deletionLinkPattern,
            )
        }
        set(value) {
            dankChatPreferences.edit {
                putString(UPLOADER_URL, value.uploadUrl)
                putString(UPLOADER_FORM_FIELD, value.formField)
                putString(UPLOADER_HEADERS, value.headers)
                putString(UPLOADER_IMAGE_LINK, value.imageLinkPattern)
                putString(UPLOADER_DELETION_LINK, value.deletionLinkPattern)
            }
        }

    var customRmHost: String
        get() = defaultPreferences.getString(context.getString(R.string.preference_rm_host_key), RM_HOST_DEFAULT) ?: RM_HOST_DEFAULT
        set(value) {
            val hostOrDefault = value.ifBlank { RM_HOST_DEFAULT }
            defaultPreferences.edit { putString(context.getString(R.string.preference_rm_host_key), hostOrDefault) }
        }

    var customMentions: List<MultiEntryDto>
        get() = getMultiEntriesFromPreferences(context.getString(R.string.preference_custom_mentions_key))
        set(value) = setMultiEntries(context.getString(R.string.preference_custom_mentions_key), value)

    var customBlacklist: List<MultiEntryDto>
        get() = getMultiEntriesFromPreferences(context.getString(R.string.preference_blacklist_key))
        set(value) = setMultiEntries(context.getString(R.string.preference_blacklist_key), value)

    var isSecretDankerModeEnabled: Boolean
        get() = dankChatPreferences.getBoolean(SECRET_DANKER_MODE_KEY, false)
        set(value) = dankChatPreferences.edit { putBoolean(SECRET_DANKER_MODE_KEY, value) }

    val commandsAsFlow: Flow<List<CommandItem.Entry>> = callbackFlow {
        val commandsKey = context.getString(R.string.preference_commands_key)
        send(getCommandsFromPreferences(commandsKey))

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == commandsKey) {
                trySend(getCommandsFromPreferences(key))
            }
        }

        defaultPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { defaultPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val visibleThirdPartyEmotes: Set<ThirdPartyEmoteType>
        get() {
            val entries = defaultPreferences.getStringSet(
                context.getString(R.string.preference_visible_emotes_key),
                context.resources.getStringArray(R.array.emotes_entry_values).toSet()
            ).orEmpty()
            return ThirdPartyEmoteType.mapFromPreferenceSet(entries)
        }

    val unlistedSevenTVEmotesEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_unlisted_emotes_key), false)

    val shouldLoadHistory: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_load_message_history_key), true)

    val shouldLoadMessagesOnReconnect: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_load_messages_on_reconnect__key), true)

    val shouldLoadSupibot: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_supibot_suggestions_key), false)

    val scrollbackLength: Int
        get() = correctScrollbackLength(defaultPreferences.getInt(context.getString(R.string.preference_scrollback_length_key), 10))

    val fetchStreamInfoEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_fetch_streams_key), true)

    val shouldPreferEmoteSuggestions: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_prefer_emote_suggestions_key), false)

    val autoDisableInput: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_auto_disable_input_key), true)

    val repeatedSendingEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_repeated_sending_key), false)

    val retainWebViewEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_retain_webview_key), false)

    val mentionEntries: Set<String>
        get() = defaultPreferences.getStringSet(context.getString(R.string.preference_custom_mentions_key), emptySet()).orEmpty()

    val blackListEntries: Set<String>
        get() = defaultPreferences.getStringSet(context.getString(R.string.preference_blacklist_key), emptySet()).orEmpty()

    val showTimestamps: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_timestamp_key), true)

    val fontSize: Float
        get() = defaultPreferences.getInt(context.getString(R.string.preference_font_size_key), 14).toFloat()

    val isCheckeredMode: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.checkered_messages_key), false)

    val showTimedOutMessages: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_show_timed_out_messages_key), true)

    val showUsername: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_show_username_key), true)

    val animateGifs: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_animate_gifs_key), true)

    val visibleBadgeTypes: Set<BadgeType>
        get() {
            val raw = defaultPreferences.getStringSet(
                context.getString(R.string.preference_visible_badges_key),
                context.resources.getStringArray(R.array.badges_entry_values).toSet()
            ).orEmpty()
            return BadgeType.mapFromPreferenceSet(raw)
        }

    val debugEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_debug_mode_key), false)

    val createNotifications: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_notification_key), true)

    val createWhisperNotifications: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_notification_whisper_key), true)

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

    val preferenceFlow: Flow<Preference> = callbackFlow {
        with(context) {
            val roomStateKey = getString(R.string.preference_roomstate_key)
            val streamInfoKey = getString(R.string.preference_streaminfo_key)
            val inputKey = getString(R.string.preference_show_input_key)
            val loadSupibotKey = getString(R.string.preference_supibot_suggestions_key)
            val scrollBackLengthKey = getString(R.string.preference_scrollback_length_key)
            val showChipsKey = getString(R.string.preference_show_chip_actions_key)
            val timestampFormatKey = getString(R.string.preference_timestamp_format_key)
            val fetchStreamsKey = getString(R.string.preference_fetch_streams_key)

            send(Preference.RoomState(roomStateEnabled))
            send(Preference.StreamInfo(showStreamInfoEnabled, updateTimer = false))
            send(Preference.Input(inputEnabled))
            send(Preference.ScrollBack(scrollbackLength))
            send(Preference.Chips(shouldShowChips))
            send(Preference.TimeStampFormat(timestampFormat))

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                val preference = when (key) {
                    roomStateKey        -> Preference.RoomState(roomStateEnabled)
                    streamInfoKey       -> Preference.StreamInfo(showStreamInfoEnabled, updateTimer = true)
                    inputKey            -> Preference.Input(inputEnabled)
                    loadSupibotKey      -> Preference.SupibotSuggestions(shouldLoadSupibot)
                    scrollBackLengthKey -> Preference.ScrollBack(scrollbackLength)
                    showChipsKey        -> Preference.Chips(shouldShowChips)
                    timestampFormatKey  -> Preference.TimeStampFormat(timestampFormat)
                    fetchStreamsKey     -> Preference.FetchStreams(fetchStreamInfoEnabled)
                    else                -> null
                }
                if (preference != null) {
                    trySend(preference)
                }
            }

            defaultPreferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { defaultPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
    }

    fun formatViewersString(viewers: Int): String = context.resources.getQuantityString(R.plurals.viewers, viewers, viewers)

    fun clearLogin() = dankChatPreferences.edit {
        putBoolean(LOGGED_IN_KEY, false)
        putString(OAUTH_KEY, null)
        putString(NAME_KEY, null)
        putString(ID_STRING_KEY, null)
    }

    fun clearBlacklist() = defaultPreferences.edit {
        remove(context.getString(R.string.preference_blacklist_key))
    }

    fun clearCustomMentions() = defaultPreferences.edit {
        remove(context.getString(R.string.preference_custom_mentions_key))
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

    fun resetImageUploader(): ImageUploader {
        customImageUploader = DEFAULT_UPLOADER
        return DEFAULT_UPLOADER
    }

    fun resetRmHost(): String {
        customRmHost = RM_HOST_DEFAULT
        return RM_HOST_DEFAULT
    }

    fun shouldShowChangelog(): Boolean {
        if (!shouldShowChangelogAfterUpdate) {
            setCurrentInstalledVersionCode()
            return false
        }

        val current = DankChatVersion.CURRENT ?: return false
        val lastViewed = lastViewedChangelogVersion?.let { DankChatVersion.fromString(it) } ?: return true
        return lastViewed < current
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

    private fun getMultiEntriesFromPreferences(key: String): List<MultiEntryDto> {
        return defaultPreferences
            .getStringSet(key, emptySet())
            .orEmpty()
            .mapNotNull { json.decodeOrNull<MultiEntryDto>(it) }
    }

    private fun setMultiEntries(key: String, entries: List<MultiEntryDto>) {
        entries.map { json.encodeToString(it) }
            .toSet()
            .let {
                defaultPreferences.edit { putStringSet(key, it) }
            }
    }

    private fun getCommandsFromPreferences(key: String): List<CommandItem.Entry> {
        return defaultPreferences
            .getStringSet(key, emptySet())
            .orEmpty()
            .mapNotNull { json.decodeOrNull<CommandDto>(it)?.toEntryItem() }
    }

    private fun Map<UserName, UserName>.toJson(): String = json.encodeToString(this)

    private val timestampFormat: String
        get() = defaultPreferences.getString(context.getString(R.string.preference_timestamp_format_key), "HH:mm") ?: "HH:mm"

    private val roomStateEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_roomstate_key), true)

    private val inputEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_show_input_key), true)

    private val shouldShowChips: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_show_chip_actions_key), true)

    private val showStreamInfoEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_streaminfo_key), true)

    private var lastViewedChangelogVersion: String?
        get() = dankChatPreferences.getString(LAST_INSTALLED_VERSION_KEY, null)
        set(value) = dankChatPreferences.edit { putString(LAST_INSTALLED_VERSION_KEY, value) }

    private val shouldShowChangelogAfterUpdate: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_show_changelogs_key), true)

    companion object {
        private const val LOGGED_IN_KEY = "loggedIn"
        private const val OAUTH_KEY = "oAuthKey"
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

        private const val UPLOADER_URL = "uploaderUrl"
        private const val UPLOADER_FORM_FIELD = "uploaderFormField"
        private const val UPLOADER_HEADERS = "uploaderHeaders"
        private const val UPLOADER_IMAGE_LINK = "uploaderImageLink"
        private const val UPLOADER_DELETION_LINK = "uploaderDeletionLink"

        private const val UPLOADER_URL_DEFAULT = "https://kappa.lol/api/upload"
        private const val UPLOADER_FORM_FIELD_DEFAULT = "file"
        private const val UPLOADER_IMAGE_LINK_DEFAULT = "{link}"
        private const val UPLOADER_DELETE_LINK_DEFAULT = "{delete}"

        private const val RM_HOST_DEFAULT = "https://recent-messages.robotty.de/api/v2/"

        private const val SCROLLBACK_LENGTH_STEP = 50
        fun correctScrollbackLength(seekbarValue: Int): Int = seekbarValue * SCROLLBACK_LENGTH_STEP

        val DEFAULT_UPLOADER = ImageUploader(
            uploadUrl = UPLOADER_URL_DEFAULT,
            formField = UPLOADER_FORM_FIELD_DEFAULT,
            headers = null,
            imageLinkPattern = UPLOADER_IMAGE_LINK_DEFAULT,
            deletionLinkPattern = UPLOADER_DELETE_LINK_DEFAULT,
        )
    }
}
