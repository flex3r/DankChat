package com.flxrs.dankchat.data.api.eventapi

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.eventapi.dto.EventSubMethod
import com.flxrs.dankchat.data.api.eventapi.dto.EventSubModeratorConditionDto
import com.flxrs.dankchat.data.api.eventapi.dto.EventSubSubscriptionRequestDto
import com.flxrs.dankchat.data.api.eventapi.dto.EventSubSubscriptionType
import com.flxrs.dankchat.data.api.eventapi.dto.EventSubTransportDto

sealed interface EventSubTopic {
    fun createRequest(sessionId: String): EventSubSubscriptionRequestDto

    data class ChannelModerate(
        val channel: UserName,
        val broadcasterId: UserId,
        val moderatorId: UserId,
    ) : EventSubTopic {
        override fun createRequest(sessionId: String) = EventSubSubscriptionRequestDto(
            type = EventSubSubscriptionType.ChannelModerate,
            version = "2",
            condition = EventSubModeratorConditionDto(
                broadcasterUserId = broadcasterId,
                moderatorUserId = moderatorId,
            ),
            transport = EventSubTransportDto(
                sessionId = sessionId,
                method = EventSubMethod.Websocket,
            ),
        )
    }
}

data class SubscribedTopic(val id: String, val topic: EventSubTopic)
