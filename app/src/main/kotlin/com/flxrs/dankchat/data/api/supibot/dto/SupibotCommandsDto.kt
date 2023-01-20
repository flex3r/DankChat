package com.flxrs.dankchat.data.api.supibot.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SupibotCommandsDto(@SerialName(value = "data") val data: List<SupibotCommandDto>)

