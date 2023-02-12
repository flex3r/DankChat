package com.flxrs.dankchat.data.api.badges.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class TwitchBadgeSetsDto(@SerialName(value = "badge_sets") val sets: Map<String, TwitchBadgeSetDto>)