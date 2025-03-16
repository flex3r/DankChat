package com.flxrs.dankchat.data.api.eventapi

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.eventapi.dto.messages.notification.ChannelModerateDto
import kotlinx.datetime.Instant

sealed interface EventSubMessage

data class SystemMessage(val message: String) : EventSubMessage

data class ModerationAction(
    val id: String,
    val timestamp: Instant,
    val channelName: UserName,
    val data: ChannelModerateDto,
) : EventSubMessage
