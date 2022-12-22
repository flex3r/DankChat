package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class AnnouncementRequestDto(val message: String, val color: AnnouncementColor = AnnouncementColor.Primary)

