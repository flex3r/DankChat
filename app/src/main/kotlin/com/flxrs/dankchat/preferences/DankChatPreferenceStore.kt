package com.flxrs.dankchat.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.twitch.emote.ThirdPartyEmoteType
import com.flxrs.dankchat.preferences.command.CommandItem
import com.flxrs.dankchat.preferences.upload.ImageUploader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class DankChatPreferenceStore @Inject constructor(private val context: Context) {
    private val dankChatPreferences: SharedPreferences = context.getSharedPreferences(context.getString(R.string.shared_preference_key), Context.MODE_PRIVATE)
    private val defaultPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val adapterType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    private val jsonMapAdapter = Moshi.Builder().build().adapter<Map<String, String>>(adapterType)
    private val jsonCommandAdapter = Moshi.Builder().build().adapter(CommandItem.Entry::class.java)

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

    var customImageUploader: ImageUploader
        get() {
            val url = dankChatPreferences.getString(UPLOADER_URL, UPLOADER_URL_DEFAULT) ?: UPLOADER_URL_DEFAULT
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

    val shouldLoadSupibot: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_supibot_suggestions_key), false)

    val scrollbackLength: Int
        get() = correctScrollbackLength(defaultPreferences.getInt(context.getString(R.string.preference_scrollback_length_key), 10))

    val streamInfoEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_streaminfo_key), true)

    val shouldPreferEmoteSuggestions: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_prefer_emote_suggestions_key), false)

    val preferenceFlow: Flow<Preference> = callbackFlow {
        with(context) {
            val roomStateKey = getString(R.string.preference_roomstate_key)
            val streamInfoKey = getString(R.string.preference_streaminfo_key)
            val inputKey = getString(R.string.preference_show_input_key)
            val customMentionsKey = getString(R.string.preference_custom_mentions_key)
            val blacklistKey = getString(R.string.preference_blacklist_key)
            val loadSupibotKey = getString(R.string.preference_supibot_suggestions_key)
            val scrollBackLengthKey = getString(R.string.preference_scrollback_length_key)
            val showChipsKey = getString(R.string.preference_show_chip_actions_key)
            val timestampFormatKey = getString(R.string.preference_timestamp_format_key)

            send(Preference.RoomState(roomStateEnabled))
            send(Preference.StreamInfo(streamInfoEnabled, updateTimer = false))
            send(Preference.Input(inputEnabled))
            send(Preference.CustomMentions(mentionEntries))
            send(Preference.BlackList(blackListEntries))
            send(Preference.ScrollBack(scrollbackLength))
            send(Preference.Chips(shouldShowChips))
            send(Preference.TimeStampFormat(timestampFormat))

            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                val preference = when (key) {
                    roomStateKey        -> Preference.RoomState(roomStateEnabled)
                    streamInfoKey       -> Preference.StreamInfo(streamInfoEnabled, updateTimer = true)
                    inputKey            -> Preference.Input(inputEnabled)
                    customMentionsKey   -> Preference.CustomMentions(mentionEntries)
                    blacklistKey        -> Preference.BlackList(blackListEntries)
                    loadSupibotKey      -> Preference.SupibotSuggestions(shouldLoadSupibot)
                    scrollBackLengthKey -> Preference.ScrollBack(scrollbackLength)
                    showChipsKey        -> Preference.Chips(shouldShowChips)
                    timestampFormatKey  -> Preference.TimeStampFormat(timestampFormat)
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

    fun resetImageUploader(): ImageUploader {
        return ImageUploader(
            uploadUrl = UPLOADER_URL_DEFAULT,
            formField = UPLOADER_FORM_FIELD_DEFAULT,
            headers = null,
            imageLinkPattern = null,
            deletionLinkPattern = null
        ).apply { customImageUploader = this }
    }

    fun getCommands(): List<CommandItem.Entry> {
        val key = context.getString(R.string.preference_commands_key)
        return getCommandsFromPreferences(key)
    }

    private inline fun withChannelRenames(block: MutableMap<String, String>.() -> Unit) {
        val renameMap = channelRenames?.toMutableMap() ?: mutableMapOf()
        renameMap.block()
        channelRenames = renameMap.toJson()
    }

    private fun String.toMutableMap(): MutableMap<String, String> {
        return kotlin.runCatching {
            jsonMapAdapter.fromJson(this)?.toMutableMap() ?: mutableMapOf()
        }.getOrDefault(mutableMapOf())
    }

    private fun getCommandsFromPreferences(key: String): List<CommandItem.Entry> {
        return kotlin.runCatching {
            defaultPreferences
                .getStringSet(key, emptySet())
                .orEmpty()
                .mapNotNull { jsonCommandAdapter.fromJson(it) }
        }.getOrDefault(emptyList())
    }

    private fun Map<String, String>.toJson(): String = jsonMapAdapter.toJson(this)

    private val timestampFormat: String
        get() = defaultPreferences.getString(context.getString(R.string.preference_timestamp_format_key), "HH:mm") ?: "HH:mm"

    private val roomStateEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_roomstate_key), true)

    private val inputEnabled: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_show_input_key), true)

    private val shouldShowChips: Boolean
        get() = defaultPreferences.getBoolean(context.getString(R.string.preference_show_chip_actions_key), true)

    private val mentionEntries: Set<String>
        get() = defaultPreferences.getStringSet(context.getString(R.string.preference_custom_mentions_key), emptySet()).orEmpty()

    private val blackListEntries: Set<String>
        get() = defaultPreferences.getStringSet(context.getString(R.string.preference_blacklist_key), emptySet()).orEmpty()

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

        private const val UPLOADER_URL = "uploaderUrl"
        private const val UPLOADER_FORM_FIELD = "uploaderFormField"
        private const val UPLOADER_HEADERS = "uploaderHeaders"
        private const val UPLOADER_IMAGE_LINK = "uploaderImageLink"
        private const val UPLOADER_DELETION_LINK = "uploaderDeletionLink"

        private const val UPLOADER_URL_DEFAULT = "https://i.nuuls.com/upload"
        private const val UPLOADER_FORM_FIELD_DEFAULT = "file"

        private const val SCROLLBACK_LENGTH_STEP = 50
        fun correctScrollbackLength(seekbarValue: Int): Int = seekbarValue * SCROLLBACK_LENGTH_STEP

        val DEFAULT_UPLOADER = ImageUploader(
            uploadUrl = UPLOADER_URL_DEFAULT,
            formField = UPLOADER_FORM_FIELD_DEFAULT,
            headers = null,
            imageLinkPattern = null,
            deletionLinkPattern = null,
        )
    }
}