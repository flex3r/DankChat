package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class UserState(
    val userId: UserId? = null,
    val color: String? = null,
    val displayName: DisplayName? = null,
    val globalEmoteSets: List<String> = listOf(),
    val followerEmoteSets: Map<UserName, List<String>> = emptyMap(),
    val moderationChannels: Set<UserName> = emptySet(),
    val vipChannels: Set<UserName> = emptySet(),
) {

    fun getSendDelay(channel: UserName): Duration = when {
        hasHighRateLimit(channel) -> LOW_SEND_DELAY
        else                      -> REGULAR_SEND_DELAY
    }

    private fun hasHighRateLimit(channel: UserName): Boolean = channel in moderationChannels || channel in vipChannels

    companion object {
        private val REGULAR_SEND_DELAY = 1200.milliseconds
        private val LOW_SEND_DELAY = 150.milliseconds
    }
}
