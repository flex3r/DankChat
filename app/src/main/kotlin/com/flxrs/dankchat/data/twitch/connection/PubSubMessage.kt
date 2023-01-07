package com.flxrs.dankchat.data.twitch.connection

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.connection.dto.moderation.ModerationActionData
import com.flxrs.dankchat.data.twitch.connection.dto.redemption.PointRedemptionData
import com.flxrs.dankchat.data.twitch.connection.dto.whisper.WhisperData
import java.time.Instant

sealed class PubSubMessage {
    data class PointRedemption(
        val timestamp: Instant,
        val channelName: UserName,
        val channelId: UserId,
        val data: PointRedemptionData
    ) : PubSubMessage()

    data class Whisper(val data: WhisperData) : PubSubMessage()

    data class ModeratorAction(
        val timestamp: Instant,
        val channelId: UserId,
        val data: ModerationActionData
    ) : PubSubMessage()
}
