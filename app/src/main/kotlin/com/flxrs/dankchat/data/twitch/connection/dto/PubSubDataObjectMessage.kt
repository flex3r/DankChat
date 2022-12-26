package com.flxrs.dankchat.data.twitch.connection.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PubSubDataObjectMessage<T>(
    val type: String,
    @SerialName("data_object") val data: T
)