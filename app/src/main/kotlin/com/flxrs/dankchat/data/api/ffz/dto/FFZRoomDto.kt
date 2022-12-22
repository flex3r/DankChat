package com.flxrs.dankchat.data.api.ffz.dto

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FFZRoomDto(
    @SerialName(value = "mod_urls") val modBadgeUrls: Map<String, String?>?,
    @SerialName(value = "vip_badge") val vipBadgeUrls: Map<String, String?>?,
)