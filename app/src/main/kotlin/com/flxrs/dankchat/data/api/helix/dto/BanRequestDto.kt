package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class BanRequestDto(val data: BanRequestDataDto)
