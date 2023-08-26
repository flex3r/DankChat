package com.flxrs.dankchat.data.api.seventv.eventapi.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator(discriminator = "op")
sealed interface DataMessage {
    val d: Data
}

interface Data
