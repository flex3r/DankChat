package com.flxrs.dankchat.data.twitch.pubsub.dto.redemption

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PointRedemptionReward(
    val id: String = "",
    val title: String = "",
    val cost: Int = 0,
    @SerialName("is_user_input_required") val requiresUserInput: Boolean = false,
    @SerialName("image") val images: PointRedemptionImages? = null,
    @SerialName("default_image") val defaultImages: PointRedemptionImages? = null,
    @SerialName("reward_type") val rewardType: String? = null,
    @SerialName("bits_cost") val bitsCost: Int = 0,
    @SerialName("default_bits_cost") val defaultBitsCost: Int = 0,
    @SerialName("pricing_type") val pricingType: String? = null,
) {
    val effectiveId: String
        get() = when (rewardType) {
            "SEND_GIGANTIFIED_EMOTE" -> "gigantified-emote-message"
            "SEND_ANIMATED_MESSAGE" -> "animated-message"
            else -> id
        }

    // Only these redemptions are followed by a chat message that carries the reward details
    val hasChatMessage: Boolean
        get() = requiresUserInput || rewardType == "SEND_GIGANTIFIED_EMOTE" || rewardType == "SEND_ANIMATED_MESSAGE"

    val effectiveTitle: String
        get() = title.ifEmpty {
            when (rewardType) {
                "SEND_GIGANTIFIED_EMOTE" -> "Gigantify an Emote"
                "SEND_ANIMATED_MESSAGE" -> "Message Effects"
                else -> ""
            }
        }

    val effectiveCost: Int
        get() = when {
            cost > 0 -> cost
            bitsCost > 0 -> bitsCost
            defaultBitsCost > 0 -> defaultBitsCost
            else -> 0
        }
}
