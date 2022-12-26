package com.flxrs.dankchat.data.twitch.connection.dto

import kotlinx.serialization.Serializable

@Serializable
data class PubSubDataMessage<T>(
    val type: String,
    val data: T
)