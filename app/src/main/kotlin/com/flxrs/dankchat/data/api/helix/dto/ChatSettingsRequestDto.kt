package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ChatSettingsRequestDto(
    @SerialName("emote_mode") val emoteMode: Boolean? = null,
    @SerialName("follower_mode") val followerMode: Boolean? = null,
    @SerialName("follower_mode_duration") val followerModeDuration: Int? = null,
    @SerialName("non_moderator_chat_delay") val nonModeratorChatDelay: Boolean? = null,
    @SerialName("non_moderator_chat_delay_duration") val nonModeratorChatDelayDuration: Int? = null,
    @SerialName("slow_mode") val slowMode: Boolean? = null,
    @SerialName("slow_mode_wait_time") val slowModeWaitTime: Int? = null,
    @SerialName("subscriber_mode") val subscriberMode: Boolean? = null,
    @SerialName("unique_chat_mode") val uniqueChatMode: Boolean? = null,
)
