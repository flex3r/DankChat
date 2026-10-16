package com.flxrs.dankchat.preferences.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.auth.AuthSettings
import com.flxrs.dankchat.data.repo.emote.EmoteRepository
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.preferences.dankchataccount.KnownBadgeOwners
import com.flxrs.dankchat.preferences.dankchataccount.KnownBadgeOwnersDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class OverviewSettingsViewModel(
    authDataStore: AuthDataStore,
    emoteRepository: EmoteRepository,
    knownBadgeOwnersDataStore: KnownBadgeOwnersDataStore,
) : ViewModel() {
    val showDankChatAccount: StateFlow<Boolean> =
        combine(
            authDataStore.settings,
            emoteRepository.dankChatBadges,
            knownBadgeOwnersDataStore.settings,
            ::ownsDankChatBadge,
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                ownsDankChatBadge(
                    auth = authDataStore.current(),
                    badges = emoteRepository.dankChatBadges.value,
                    knownOwners = knownBadgeOwnersDataStore.currentSettings.value,
                ),
        )

    private fun ownsDankChatBadge(
        auth: AuthSettings,
        badges: Map<UserId, Badge.DankChatBadge>,
        knownOwners: KnownBadgeOwners,
    ): Boolean {
        val userId = auth.userId?.toUserId()
        return when {
            !auth.isLoggedIn || userId == null -> false
            else -> userId in badges || userId in knownOwners.userIds
        }
    }
}
