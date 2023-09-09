package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("7")
data class EndOfStreamMessage(override val d: EndOfStreamData) : DataMessage

@Serializable
data object EndOfStreamData : Data
