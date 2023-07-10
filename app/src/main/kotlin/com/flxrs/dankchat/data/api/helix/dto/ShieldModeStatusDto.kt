package com.flxrs.dankchat.data.api.helix.dto

import androidx.annotation.Keep
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class ShieldModeStatusDto(
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("moderator_id") val moderatorId: UserId,
    @SerialName("moderator_login") val moderatorLogin: UserName,
    @SerialName("moderator_name") val moderatorName: DisplayName,
    @SerialName("last_activated_at") val lastActivatedAt: Instant,
)
