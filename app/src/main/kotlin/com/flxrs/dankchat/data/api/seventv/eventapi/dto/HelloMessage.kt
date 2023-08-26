package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("1")
data class HelloMessage(override val d: HelloData) : DataMessage

@Serializable
data class HelloData(
    @SerialName("heartbeat_interval") val heartBeatInterval: Int,
    @SerialName("session_id") val sessionId: String,
) : Data
