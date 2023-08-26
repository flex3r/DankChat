package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("4")
data class ReconnectMessage(override val d: ReconnectData) : DataMessage

@Serializable
data object ReconnectData : Data
