package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class AnnouncementColor {
    @SerialName("primary")
    Primary,
    @SerialName("blue")
    Blue,
    @SerialName("green")
    Green,
    @SerialName("orange")
    Orange,
    @SerialName("purple")
    Purple
}