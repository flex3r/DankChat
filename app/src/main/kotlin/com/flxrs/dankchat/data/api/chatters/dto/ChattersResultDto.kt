package com.flxrs.dankchat.data.api.chatters.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ChattersResultDto(@SerialName(value = "chatters") val chatters: ChattersDto)