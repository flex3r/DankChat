package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class BadgeSetDto(@SerialName("set_id") val id: String, val versions: List<BadgeDto>)
