package com.flxrs.dankchat.data.twitch.pubsub.dto.moderation

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ModeratorAddedData(
    @SerialName("channel_id") val channelId: UserId,
    @SerialName("target_user_id") val targetUserId: UserId,
    @SerialName("target_user_login") val targetUserName: UserName,
    @SerialName("moderation_action") val moderationAction: ModerationActionType,
    @SerialName("created_by_user_id") val creatorUserId: UserId,
    @SerialName("created_by") val creator: UserName,
)
