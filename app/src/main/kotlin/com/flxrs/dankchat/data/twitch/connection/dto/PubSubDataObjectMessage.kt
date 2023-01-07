package com.flxrs.dankchat.data.twitch.connection.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PubSubDataObjectMessage<T>(
    val type: String,
    @SerialName("data_object") val data: T
)
