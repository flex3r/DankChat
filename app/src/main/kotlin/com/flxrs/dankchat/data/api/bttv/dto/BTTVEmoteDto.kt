package com.flxrs.dankchat.data.api.bttv.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class BTTVEmoteDto(
    val id: String,
    val code: String,
    val user: BTTVEmoteUserDto?,
)

