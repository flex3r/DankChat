package com.flxrs.dankchat.data.api.supibot.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.UserName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SupibotChannelDto(@SerialName(value = "name") val name: UserName, @SerialName(value = "mode") val mode: String) {
    val isActive: Boolean
        get() = mode != "Last seen" && mode != "Read"
}