package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVUserConnection(val platform: String) {
    companion object {
        const val twitch = "TWITCH"
    }
}
