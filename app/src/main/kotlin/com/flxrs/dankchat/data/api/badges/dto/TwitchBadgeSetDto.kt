package com.flxrs.dankchat.data.api.badges.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class TwitchBadgeSetDto(@SerialName(value = "versions") val versions: Map<String, TwitchBadgeDto>)