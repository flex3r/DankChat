package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.irc.IrcMessage

data class RoomState(
    val channel: UserName,
    val channelId: UserId,
    val tags: Map<RoomStateTag, Int> = mapOf(
        RoomStateTag.EMOTE to 0,
        RoomStateTag.SUBS to 0,
        RoomStateTag.SLOW to 0,
        RoomStateTag.R9K to 0,
        RoomStateTag.FOLLOW to -1,
    )
) {
    val activeStates: BooleanArray
        get() = tags.entries.map { (tag, value) ->
            if (tag == RoomStateTag.FOLLOW) value >= 0 else value > 0
        }.toBooleanArray()

    val isEmoteMode get() = tags.getOrDefault(RoomStateTag.EMOTE, 0) > 0
    val isSubscriberMode get() = tags.getOrDefault(RoomStateTag.SUBS, 0) > 0
    val isSlowMode get() = tags.getOrDefault(RoomStateTag.SLOW, 0) > 0
    val isUniqueChatMode get() = tags.getOrDefault(RoomStateTag.R9K, 0) > 0
    val isFollowMode get() = tags.getOrDefault(RoomStateTag.FOLLOW, 0) >= 0

    val followerModeDuration get() = tags[RoomStateTag.FOLLOW]?.takeIf { it >= 0 }
    val slowModeWaitTime get() = tags[RoomStateTag.SLOW]?.takeIf { it > 0 }

    fun toDisplayText(): String = tags
        .filter { (it.key == RoomStateTag.FOLLOW && it.value >= 0) || it.value > 0 }
        .map {
            when (it.key) {
                RoomStateTag.FOLLOW -> if (it.value == 0) "follow" else "follow(${it.value})"
                RoomStateTag.SLOW   -> "slow(${it.value})"
                else                -> it.key.name.lowercase()
            }
        }.joinToString()

    fun copyFromIrcMessage(msg: IrcMessage): RoomState = copy(
        tags = tags.mapValues { (key, value) -> msg.getRoomStateTag(key, value) },
    )

    private fun IrcMessage.getRoomStateTag(tag: RoomStateTag, default: Int): Int = tags[tag.ircTag]?.toIntOrNull() ?: default
}
