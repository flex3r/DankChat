package com.flxrs.dankchat.preferences.dankchataccount

import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.Serializable

/** Users that own DankChat badges, also when they chose to show none. */
@Serializable
data class KnownBadgeOwners(
    val userIds: Set<UserId> = emptySet(),
)
