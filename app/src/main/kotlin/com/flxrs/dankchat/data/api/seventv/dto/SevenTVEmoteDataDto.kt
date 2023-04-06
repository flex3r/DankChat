package com.flxrs.dankchat.data.api.seventv.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class SevenTVEmoteDataDto(
    val listed: Boolean,
    val animated: Boolean,
    val flags: Long,
    val host: SevenTVEmoteHostDto,
    val owner: SevenTVEmoteOwnerDto?,
    @SerialName("name") val baseName: String,
) {

    val isTwitchDisallowed get() = (TWITCH_DISALLOWED_FLAG and flags) == TWITCH_DISALLOWED_FLAG

    companion object {
        private const val TWITCH_DISALLOWED_FLAG = 1L shl 24
    }
}
