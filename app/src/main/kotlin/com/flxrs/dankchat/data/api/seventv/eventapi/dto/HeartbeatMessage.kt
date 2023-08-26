package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("2")
data class HeartbeatMessage(override val d: HeartbeatData) : DataMessage

@Serializable
data class HeartbeatData(val count: Int) : Data
