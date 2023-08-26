package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("5")
data class AckMessage(override val d: AckData) : DataMessage

@Serializable
data object AckData : Data
