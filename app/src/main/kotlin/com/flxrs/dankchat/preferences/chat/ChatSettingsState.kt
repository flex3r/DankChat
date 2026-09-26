package com.flxrs.dankchat.preferences.chat

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

sealed interface ChatSettingsEvent {
    data object RestartRequired : ChatSettingsEvent
}

sealed interface ChatSettingsInteraction {
    data class SuggestionTypes(
        val value: List<SuggestionType>,
    ) : ChatSettingsInteraction

    data class SuggestionModeChange(
        val value: SuggestionMode,
    ) : ChatSettingsInteraction

    data class CustomCommands(
        val value: List<CustomCommand>,
    ) : ChatSettingsInteraction

    data class AnimateGifs(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class ScrollbackLength(
        val value: Int,
    ) : ChatSettingsInteraction

    data class ShowUsernames(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class UserLongClick(
        val value: UserLongClickBehavior,
    ) : ChatSettingsInteraction

    data class ColorizeNicknames(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class BoldUsernameMentions(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class ColorUsernameMentions(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class ShowTimedOutMessages(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class ShowTimestamps(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class TimestampFormat(
        val value: String,
    ) : ChatSettingsInteraction

    data class Badges(
        val value: List<VisibleBadges>,
    ) : ChatSettingsInteraction

    data class Emotes(
        val value: List<VisibleThirdPartyEmotes>,
    ) : ChatSettingsInteraction

    data class AllowUnlisted(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class LiveEmoteUpdates(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class MessageHistory(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class MessageHistoryAfterReconnect(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class ChatModes(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class AlwaysShowPinnedMessage(
        val value: Boolean,
    ) : ChatSettingsInteraction

    data class ShowStreamTitleInLiveMessage(
        val value: Boolean,
    ) : ChatSettingsInteraction
}

@Immutable
data class ChatSettingsState(
    val suggestionTypes: ImmutableList<SuggestionType>,
    val suggestionMode: SuggestionMode,
    val customCommands: ImmutableList<CustomCommand>,
    val animateGifs: Boolean,
    val scrollbackLength: Int,
    val showUsernames: Boolean,
    val userLongClickBehavior: UserLongClickBehavior,
    val colorizeNicknames: Boolean,
    val boldUsernameMentions: Boolean,
    val colorUsernameMentions: Boolean,
    val showTimedOutMessages: Boolean,
    val showTimestamps: Boolean,
    val timestampFormat: String,
    val visibleBadges: ImmutableList<VisibleBadges>,
    val visibleEmotes: ImmutableList<VisibleThirdPartyEmotes>,
    val allowUnlistedSevenTvEmotes: Boolean,
    val sevenTVLiveEmoteUpdates: Boolean,
    val loadMessageHistory: Boolean,
    val loadMessageHistoryAfterReconnect: Boolean,
    val messageHistoryDashboardUrl: String,
    val showChatModes: Boolean,
    val alwaysShowPinnedMessage: Boolean,
    val showStreamTitleInLiveMessage: Boolean,
)
