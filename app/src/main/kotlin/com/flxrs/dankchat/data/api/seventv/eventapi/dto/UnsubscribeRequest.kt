package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import kotlinx.serialization.Serializable

@Serializable
data class UnsubscribeRequest(override val op: Int = 36, override val d: SubscriptionData) : DataRequest {
    companion object {
        fun userUpdates(userId: String) = UnsubscribeRequest(
            d = SubscriptionData(type = SubscriptionType.UserUpdates.type, condition = SubscriptionCondition(objectId = userId))
        )

        fun emoteSetUpdates(emoteSetId: String) = UnsubscribeRequest(
            d = SubscriptionData(type = SubscriptionType.EmoteSetUpdates.type, condition = SubscriptionCondition(objectId = emoteSetId))
        )
    }
}
