package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class SevenTVEmoteVisibility {
    PRIVATE,
    GLOBAL,
    UNLISTED,
    OVERRIDE_FFZ,
    OVERRIDE_BTTV,
    OVERRIDE_TWITCH_SUBSCRIBER,
    OVERRIDE_TWITCH_GLOBAL,
    ZERO_WIDTH
}