package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.twitch.connection.PointRedemptionData
import com.flxrs.dankchat.preferences.userdisplay.UserFinalizedDisplay
import java.time.Instant
import java.time.ZoneId
import java.util.*

data class PointRedemptionMessage(
    override val timestamp: Long = System.currentTimeMillis(),
    override val id: String = UUID.randomUUID().toString(),
    override val highlights: List<Highlight> = emptyList(),
    val name: String,
    val displayName: String,
    val title: String,
    val rewardImageUrl: String,
    val cost: Int,
    val requiresUserInput: Boolean,
    val userDisplay: UserFinalizedDisplay? = null,
) : Message() {


    // final (effective) name/color to be shown, according to override logic
    val finalName: String get() = userDisplay?.username ?: name
    val finalDisplayName: String get() = userDisplay?.displayName ?: displayName

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