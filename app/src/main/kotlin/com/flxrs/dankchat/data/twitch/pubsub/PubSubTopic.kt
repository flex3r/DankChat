package com.flxrs.dankchat.data.twitch.pubsub

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName

sealed class PubSubTopic(val topic: String) {
    data class PointRedemptions(val channelId: UserId, val channelName: UserName) : PubSubTopic(topic = "community-points-channel-v1.$channelId")
    data class Whispers(val userId: UserId) : PubSubTopic(topic = "whispers.$userId")
    data class ModeratorActions(val userId: UserId, val channelId: UserId) : PubSubTopic(topic = "chat_moderator_actions.$userId.$channelId")
}
