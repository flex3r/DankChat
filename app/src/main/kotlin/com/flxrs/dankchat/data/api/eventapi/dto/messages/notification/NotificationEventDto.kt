package com.flxrs.dankchat.data.api.eventapi.dto.messages.notification

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface NotificationEventDto
