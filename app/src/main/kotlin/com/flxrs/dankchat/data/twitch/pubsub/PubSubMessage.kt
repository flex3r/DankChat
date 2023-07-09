package com.flxrs.dankchat.data.twitch.pubsub

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.pubsub.dto.moderation.ModerationActionData
import com.flxrs.dankchat.data.twitch.pubsub.dto.redemption.PointRedemptionData
import com.flxrs.dankchat.data.twitch.pubsub.dto.whisper.WhisperData
import java.time.Instant

sealed interface PubSubMessage {
    data class PointRedemption(
        val timestamp: Instant,
        val channelName: UserName,
        val channelId: UserId,
        val data: PointRedemptionData
    ) : PubSubMessage

    data class Whisper(val data: WhisperData) : PubSubMessage

    data class ModeratorAction(
        val timestamp: Instant,
        val channelId: UserId,
        val data: ModerationActionData
    ) : PubSubMessage
}
