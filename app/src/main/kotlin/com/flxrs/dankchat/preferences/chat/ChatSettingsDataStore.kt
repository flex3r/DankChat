package com.flxrs.dankchat.preferences.chat

import android.content.Context
import com.flxrs.dankchat.R
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.datastore.PreferenceKeys
import com.flxrs.dankchat.utils.datastore.booleanOrDefault
import com.flxrs.dankchat.utils.datastore.booleanOrNull
import com.flxrs.dankchat.utils.datastore.createDataStore
import com.flxrs.dankchat.utils.datastore.dankChatPreferencesMigration
import com.flxrs.dankchat.utils.datastore.intOrDefault
import com.flxrs.dankchat.utils.datastore.mappedStringOrDefault
import com.flxrs.dankchat.utils.datastore.mappedStringSetOrDefault
import com.flxrs.dankchat.utils.datastore.safeData
import com.flxrs.dankchat.utils.datastore.stringOrDefault
import com.flxrs.dankchat.utils.datastore.stringSetOrNull
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class ChatSettingsDataStore(
    context: Context,
    dispatchersProvider: DispatchersProvider,
) {

    private enum class ChatPreferenceKeys(override val id: Int) : PreferenceKeys {
        Suggestions(R.string.preference_suggestions_key),
        PreferEmoteSuggestions(R.string.preference_prefer_emote_suggestions_key),
        SupibotSuggestions(R.string.preference_supibot_suggestions_key),
        CustomCommands(R.string.preference_commands_key),
        AnimateGifs(R.string.preference_animate_gifs_key),
        ScrollbackLength(R.string.preference_scrollback_length_key),
        ShowUsernames(R.string.preference_show_username_key),
        UserLongClickBehavior(R.string.preference_user_long_click_key),
        ShowTimedOutMessages(R.string.preference_show_timed_out_messages_key),
        ShowTimestamps(R.string.preference_timestamp_key),
        TimestampFormat(R.string.preference_timestamp_format_key),
        VisibleBadges(R.string.preference_visible_badges_key),
        VisibleEmotes(R.string.preference_visible_emotes_key),
        UnlistedEmotes(R.string.preference_unlisted_emotes_key),
        LiveUpdates(R.string.preference_7tv_live_updates_key),
        LiveUpdatesTimeout(R.string.preference_7tv_live_updates_timeout_key),
        LoadMessageHistory(R.string.preference_load_message_history_key),
        ShowRoomState(R.string.preference_roomstate_key),
    }

    private val initialMigration = dankChatPreferencesMigration<ChatPreferenceKeys, ChatSettings>(context) { acc, key, value ->
        when (key) {
            ChatPreferenceKeys.Suggestions -> acc.copy(suggestions = value.booleanOrDefault(acc.suggestions))
            ChatPreferenceKeys.PreferEmoteSuggestions -> acc.copy(preferEmoteSuggestions = value.booleanOrDefault(acc.preferEmoteSuggestions))
            ChatPreferenceKeys.SupibotSuggestions -> acc.copy(supibotSuggestions = value.booleanOrDefault(acc.supibotSuggestions))
            ChatPreferenceKeys.CustomCommands -> {
                val commands = value.stringSetOrNull()?.mapNotNull {
                    Json.decodeOrNull<CustomCommand>(it)
                } ?: acc.customCommands
                acc.copy(customCommands = commands)
            }

            ChatPreferenceKeys.AnimateGifs -> acc.copy(animateGifs = value.booleanOrDefault(acc.animateGifs))
            ChatPreferenceKeys.ScrollbackLength -> acc.copy(scrollbackLength = value.intOrDefault(acc.scrollbackLength))
            ChatPreferenceKeys.ShowUsernames -> acc.copy(showUsernames = value.booleanOrDefault(acc.showUsernames))
            ChatPreferenceKeys.UserLongClickBehavior -> acc.copy(
                userLongClickBehavior = value.booleanOrNull()?.let {
                    if (it) UserLongClickBehavior.MentionsUser else UserLongClickBehavior.OpensPopup
                } ?: acc.userLongClickBehavior
            )

            ChatPreferenceKeys.ShowTimedOutMessages -> acc.copy(showTimedOutMessages = value.booleanOrDefault(acc.showTimedOutMessages))
            ChatPreferenceKeys.ShowTimestamps -> acc.copy(showTimestamps = value.booleanOrDefault(acc.showTimestamps))
            ChatPreferenceKeys.TimestampFormat -> acc.copy(timestampFormat = value.stringOrDefault(acc.timestampFormat))
            ChatPreferenceKeys.VisibleBadges -> acc.copy(
                visibleBadges = value.mappedStringSetOrDefault(
                    original = context.resources.getStringArray(R.array.badges_entry_values),
                    enumEntries = VisibleBadges.entries,
                    default = acc.visibleBadges,
                )
            )

            ChatPreferenceKeys.VisibleEmotes -> acc.copy(
                visibleEmotes = value.mappedStringSetOrDefault(
                    original = context.resources.getStringArray(R.array.emotes_entry_values),
                    enumEntries = VisibleThirdPartyEmotes.entries,
                    default = acc.visibleEmotes,
                )
            )

            ChatPreferenceKeys.UnlistedEmotes -> acc.copy(allowUnlistedSevenTvEmotes = value.booleanOrDefault(acc.allowUnlistedSevenTvEmotes))
            ChatPreferenceKeys.LiveUpdates -> acc.copy(sevenTVLiveEmoteUpdates = value.booleanOrDefault(acc.sevenTVLiveEmoteUpdates))
            ChatPreferenceKeys.LiveUpdatesTimeout -> acc.copy(
                sevenTVLiveEmoteUpdatesBehavior = value.mappedStringOrDefault(
                    original = context.resources.getStringArray(R.array.event_api_timeout_entry_values),
                    enumEntries = LiveUpdatesBackgroundBehavior.entries,
                    default = acc.sevenTVLiveEmoteUpdatesBehavior,
                )
            )

            ChatPreferenceKeys.LoadMessageHistory -> acc.copy(loadMessageHistory = value.booleanOrDefault(acc.loadMessageHistory))
            ChatPreferenceKeys.ShowRoomState -> acc.copy(showChatModes = value.booleanOrDefault(acc.showChatModes))
        }
    }
    private val dataStore = createDataStore(
        fileName = "chat",
        context = context,
        defaultValue = ChatSettings(),
        serializer = ChatSettings.serializer(),
        scope = CoroutineScope(dispatchersProvider.io + SupervisorJob()),
        migrations = listOf(initialMigration),
    )

    val settings = dataStore.safeData(ChatSettings())
    val commands = settings.map { it.customCommands }
    val suggestions = settings.map { it.suggestions }
    val restartChat = settings.distinctUntilChanged { old, new ->
        old.showTimestamps != new.showTimestamps ||
                old.timestampFormat != new.timestampFormat ||
                old.showTimedOutMessages != new.showTimedOutMessages ||
                old.animateGifs != new.animateGifs ||
                old.showUsernames != new.showUsernames ||
                old.visibleBadges != new.visibleBadges
    }

    fun current() = runBlocking { settings.first() }

    suspend fun update(transform: suspend (ChatSettings) -> ChatSettings) {
        runCatching { dataStore.updateData(transform) }
    }
}
