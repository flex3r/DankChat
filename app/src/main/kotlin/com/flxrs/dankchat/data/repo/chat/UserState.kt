package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName

data class UserState(
    val userId: UserId? = null,
    val color: String? = null,
    val displayName: DisplayName? = null,
    val globalEmoteSets: List<String> = listOf(),
    val followerEmoteSets: Map<UserName, List<String>> = emptyMap(),
    val moderationChannels: Set<UserName> = emptySet(),
    val vipChannels: Set<UserName> = emptySet(),
) {

    fun getSendDelay(channel: UserName): Long = when {
        hasHighRateLimit(channel) -> LOW_SEND_DELAY_MS
        else                      -> REGULAR_SEND_DELAY_MS
    }

    private fun hasHighRateLimit(channel: UserName): Boolean = channel in moderationChannels || channel in vipChannels

    companion object {
        private const val REGULAR_SEND_DELAY_MS = 1200L
        private const val LOW_SEND_DELAY_MS = 150L
    }
}