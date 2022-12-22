package com.flxrs.dankchat.data.repo.chat

data class UserState(
    val userId: String = "",
    val color: String? = null,
    val displayName: String = "",
    val globalEmoteSets: List<String> = listOf(),
    val followerEmoteSets: Map<String, List<String>> = emptyMap(),
    val moderationChannels: Set<String> = emptySet(),
    val vipChannels: Set<String> = emptySet(),
) {

    fun getSendDelay(channel: String): Long = when {
        hasHighRateLimit(channel) -> LOW_SEND_DELAY_MS
        else                      -> REGULAR_SEND_DELAY_MS
    }

    private fun hasHighRateLimit(channel: String): Boolean = channel in moderationChannels || channel in vipChannels

    companion object {
        private const val REGULAR_SEND_DELAY_MS = 1200L
        private const val LOW_SEND_DELAY_MS = 150L
    }
}