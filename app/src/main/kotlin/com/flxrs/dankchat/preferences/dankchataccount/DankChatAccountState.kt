package com.flxrs.dankchat.preferences.dankchataccount

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

@Immutable
data class DankChatAccountState(
    val badge: DankChatBadgeState,
    val isOpeningAccount: Boolean,
)

@Immutable
sealed interface DankChatBadgeState {
    data object Loading : DankChatBadgeState

    data object LoggedOut : DankChatBadgeState

    /** The server doesn't accept the login, e.g. a token from a custom client id. */
    data object Unsupported : DankChatBadgeState

    data object Failed : DankChatBadgeState

    data object NoBadges : DankChatBadgeState

    data class Loaded(
        val badges: ImmutableList<DankChatBadgeItem>,
        val automaticBadge: DankChatBadgeItem,
        val choice: DankChatBadgeChoice,
        val isSaving: Boolean,
    ) : DankChatBadgeState
}

@Immutable
data class DankChatBadgeItem(
    val id: String,
    val title: String,
    val url: String,
)

@Immutable
sealed interface DankChatBadgeChoice {
    data object Automatic : DankChatBadgeChoice

    data object Hidden : DankChatBadgeChoice

    data class Badge(
        val id: String,
    ) : DankChatBadgeChoice
}

@Immutable
sealed interface DankChatAccountEvent {
    data object SaveFailed : DankChatAccountEvent

    data class OpenUrl(
        val url: String,
    ) : DankChatAccountEvent
}

@Stable
data class DankChatAccountEventsWrapper(
    val events: Flow<DankChatAccountEvent>,
)
