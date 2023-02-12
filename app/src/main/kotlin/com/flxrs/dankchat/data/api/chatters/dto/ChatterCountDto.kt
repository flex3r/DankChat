package com.flxrs.dankchat.data.api.chatters.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ChatterCountDto(@SerialName(value = "chatter_count") val chatterCount: Int)