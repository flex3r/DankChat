package com.flxrs.dankchat.preferences.chat

import com.flxrs.dankchat.data.twitch.badge.BadgeType
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

@Serializable
data class ChatSettings(
    val suggestions: Boolean = true,
    val preferEmoteSuggestions: Boolean = false,
    val supibotSuggestions: Boolean = false,
    val customCommands: List<CustomCommand> = emptyList(),
    val animateGifs: Boolean = true,
    val scrollbackLength: Int = 500,
    val showUsernames: Boolean = true,
    val userLongClickBehavior: UserLongClickBehavior = UserLongClickBehavior.MentionsUser,
    val showTimedOutMessages: Boolean = true,
    val showTimestamps: Boolean = true,
    val timestampFormat: String = DEFAULT_TIMESTAMP_FORMAT,
    val visibleBadges: List<VisibleBadges> = VisibleBadges.entries,
    val visibleEmotes: List<VisibleThirdPartyEmotes> = VisibleThirdPartyEmotes.entries,
    val allowUnlistedSevenTvEmotes: Boolean = false,
    val sevenTVLiveEmoteUpdates: Boolean = true,
    val sevenTVLiveEmoteUpdatesBehavior: LiveUpdatesBackgroundBehavior = LiveUpdatesBackgroundBehavior.FiveMinutes,
    val loadMessageHistory: Boolean = true,
    val loadMessageHistoryAfterReconnect: Boolean = true,
    val showChatModes: Boolean = true,
) {

    @Transient
    val visibleBadgeTypes = visibleBadges.map { BadgeType.entries[it.ordinal] }

    @Transient
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(timestampFormat)

    companion object {
        private const val DEFAULT_TIMESTAMP_FORMAT = "HH:mm"
    }
}

@Serializable
data class CustomCommand(val trigger: String, val command: String, @Transient val id: String = Uuid.random().toString())

enum class UserLongClickBehavior {
    MentionsUser,
    OpensPopup,
}

enum class VisibleBadges {
    Authority,
    Predictions,
    Channel,
    Subscriber,
    Vanity,
    DankChat,
}

enum class VisibleThirdPartyEmotes {
    FFZ,
    BTTV,
    SevenTV,
}

enum class LiveUpdatesBackgroundBehavior {
    Never,
    OneMinute,
    FiveMinutes,
    ThirtyMinutes,
    OneHour,
    Always,
}
