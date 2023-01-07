package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ChatSettingsDto(
    @SerialName("broadcaster_id") val broadcasterUserId: UserId,
    @SerialName("moderator_id") val moderatorUserId: UserId?,
    @SerialName("emote_mode") val emoteMode: Boolean,
    @SerialName("follower_mode") val followerMode: Boolean,
    @SerialName("follower_mode_duration") val followerModeDuration: Int?,
    @SerialName("non_moderator_chat_delay") val nonModeratorChatDelay: Boolean,
    @SerialName("non_moderator_chat_delay_duration") val nonModeratorChatDelayDuration: Int?,
    @SerialName("slow_mode") val slowMode: Boolean,
    @SerialName("slow_mode_wait_time") val slowModeWaitTime: Int?,
    @SerialName("subscriber_mode") val subscriberMode: Boolean,
    @SerialName("unique_chat_mode") val uniqueChatMode: Boolean,
)
