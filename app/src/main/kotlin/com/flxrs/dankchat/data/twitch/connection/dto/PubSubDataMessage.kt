package com.flxrs.dankchat.data.twitch.connection.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PubSubDataMessage<T>(
    val type: String,
    val data: T
)
