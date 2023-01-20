package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class RaidDto(@SerialName("created_at") val createdAt: String, @SerialName("is_mature") val isMature: Boolean)
