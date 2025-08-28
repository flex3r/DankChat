package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage
import com.flxrs.dankchat.data.toDisplayName
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Single
class UserStateRepository(private val preferenceStore: DankChatPreferenceStore) {

    private val _userState = MutableStateFlow(UserState())
    val userState = _userState.asStateFlow()

    suspend fun getLatestValidUserState(minChannelsSize: Int = 0): UserState = userState
        .filter {
            it.userId != null && it.userId.value.isNotBlank() && it.globalEmoteSets.isNotEmpty() && it.followerEmoteSets.size >= minChannelsSize
        }.take(count = 1).single()

    suspend fun tryGetUserStateOrFallback(
        minChannelsSize: Int,
        initialTimeout: Duration = IRC_TIMEOUT_DELAY,
        fallbackTimeout: Duration = IRC_TIMEOUT_SHORT_DELAY
    ): UserState? {
        return withTimeoutOrNull(initialTimeout) { getLatestValidUserState(minChannelsSize) }
            ?: withTimeoutOrNull(fallbackTimeout) { getLatestValidUserState(minChannelsSize = 0) }
    }

    fun isModeratorInChannel(channel: UserName?): Boolean = channel != null && channel in userState.value.moderationChannels

    fun getSendDelay(channel: UserName): Duration = userState.value.getSendDelay(channel)

    fun handleGlobalUserState(msg: IrcMessage) {
        val id = msg.tags["user-id"]?.toUserId()
        val sets = msg.tags["emote-sets"]?.split(",")
        val color = msg.tags["color"]?.ifBlank { null }
        val name = msg.tags["display-name"]?.toDisplayName()
        _userState.update { current ->
            current.copy(
                userId = id ?: current.userId,
                color = color ?: current.color,
                displayName = name ?: current.displayName,
                globalEmoteSets = sets ?: current.globalEmoteSets
            )
        }
    }

    fun handleUserState(msg: IrcMessage) {
        val channel = msg.params.getOrNull(0)?.substring(1)?.toUserName() ?: return
        val id = msg.tags["user-id"]?.toUserId()
        val sets = msg.tags["emote-sets"]?.split(",").orEmpty()
        val color = msg.tags["color"]?.ifBlank { null }
        val name = msg.tags["display-name"]?.toDisplayName()
        val badges = msg.tags["badges"]?.split(",")
        val hasModeration = badges?.any { it.contains("broadcaster") || it.contains("moderator") } == true
        preferenceStore.displayName = name
        _userState.update { current ->
            val followerEmotes = when {
                current.globalEmoteSets.isNotEmpty() -> sets - current.globalEmoteSets.toSet()
                else                                 -> emptyList()
            }
            val newFollowerEmoteSets = current.followerEmoteSets.toMutableMap()
            newFollowerEmoteSets[channel] = followerEmotes

            val newModerationChannels = when {
                hasModeration -> current.moderationChannels + channel
                else          -> current.moderationChannels
            }

            current.copy(
                userId = id ?: current.userId,
                color = color ?: current.color,
                displayName = name ?: current.displayName,
                followerEmoteSets = newFollowerEmoteSets,
                moderationChannels = newModerationChannels
            )
        }
    }

    fun addVipChannel(channel: UserName) {
        _userState.update { it.copy(vipChannels = it.vipChannels + channel) }
    }

    fun removeVipChannel(channel: UserName) {
        _userState.update { it.copy(vipChannels = it.vipChannels - channel) }
    }

    fun clearUserStateEmotes() {
        _userState.update { it.copy(globalEmoteSets = emptyList(), followerEmoteSets = emptyMap()) }
    }

    fun removeChannel(channel: UserName) {
        _userState.update {
            it.copy(
                moderationChannels = it.moderationChannels - channel,
                vipChannels = it.vipChannels - channel,
                followerEmoteSets = it.followerEmoteSets - channel,
            )
        }
    }

    fun clear() {
        _userState.update { UserState() }
    }

    companion object {
        private val IRC_TIMEOUT_DELAY = 5.seconds
        private val IRC_TIMEOUT_SHORT_DELAY = 1.seconds
    }
}
