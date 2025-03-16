package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.pubsub.dto.redemption.PointRedemptionData
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

data class PointRedemptionMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: Set<Highlight> = emptySet(),
    val name: UserName,
    val displayName: DisplayName,
    val title: String,
    val rewardImageUrl: String,
    val cost: Int,
    val requiresUserInput: Boolean,
    val userDisplay: UserDisplay? = null,
) : Message() {
    companion object {
        fun parsePointReward(timestamp: Instant, data: PointRedemptionData): PointRedemptionMessage {
            val timeZone = TimeZone.currentSystemDefault()
            return PointRedemptionMessage(
                timestamp = timestamp.toLocalDateTime(timeZone).toInstant(timeZone).toEpochMilliseconds(),
                id = data.id,
                name = data.user.name,
                displayName = data.user.displayName,
                title = data.reward.title,
                rewardImageUrl = data.reward.images?.imageLarge
                    ?: data.reward.defaultImages.imageLarge,
                cost = data.reward.cost,
                requiresUserInput = data.reward.requiresUserInput,
            )
        }
    }

    val aliasOrFormattedName: String
        get() = userDisplay?.alias ?: name.formatWithDisplayName(displayName)
}
