package com.flxrs.dankchat.data.api.ffz.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.DisplayName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FFZEmoteOwnerDto(@SerialName(value = "display_name") val displayName: DisplayName?)
