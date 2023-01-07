package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.connection.dto.PointRedemptionData
import java.time.Instant
import java.time.ZoneId
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
) : Message() {

    companion object {
        fun parsePointReward(timestamp: Instant, data: PointRedemptionData): PointRedemptionMessage {
            return PointRedemptionMessage(
                timestamp = timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
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
}