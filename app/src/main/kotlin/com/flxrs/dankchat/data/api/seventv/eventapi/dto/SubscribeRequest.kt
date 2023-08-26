package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import com.flxrs.dankchat.data.api.seventv.eventapi.dto.SubscriptionType.EmoteSetUpdates
import com.flxrs.dankchat.data.api.seventv.eventapi.dto.SubscriptionType.UserUpdates
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubscribeRequest(
    override val op: Int = 35,
    override val d: SubscriptionData,
) : DataRequest {
    companion object {
        fun userUpdates(userId: String) = SubscribeRequest(
            d = SubscriptionData(type = UserUpdates.type, condition = SubscriptionCondition(objectId = userId)),
        )

        fun emoteSetUpdates(emoteSetId: String) = SubscribeRequest(
            d = SubscriptionData(type = EmoteSetUpdates.type, condition = SubscriptionCondition(objectId = emoteSetId)),
        )
    }
}

@Serializable
data class SubscriptionData(val type: String, val condition: SubscriptionCondition) : RequestData

@Serializable
data class SubscriptionCondition(@SerialName("object_id") val objectId: String)
