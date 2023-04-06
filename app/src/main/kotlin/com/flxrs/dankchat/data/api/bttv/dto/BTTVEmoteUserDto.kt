package com.flxrs.dankchat.data.api.bttv.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.DisplayName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class BTTVEmoteUserDto(val displayName: DisplayName?)
