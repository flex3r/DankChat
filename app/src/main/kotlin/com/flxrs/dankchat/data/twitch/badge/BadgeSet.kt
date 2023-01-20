package com.flxrs.dankchat.data.twitch.badge

import com.flxrs.dankchat.data.api.badges.dto.TwitchBadgeSetsDto

data class BadgeSet(
    val id: String,
    val versions: Map<String, BadgeVersion>
)

data class BadgeVersion(
    val id: String,
    val title: String,
    val imageUrlLow: String,
    val imageUrlMedium: String,
    val imageUrlHigh: String
)

fun TwitchBadgeSetsDto.toBadgeSets(): Map<String, BadgeSet> = sets.mapValues { (id, set) ->
    BadgeSet(
        id = id,
        versions = set.versions.mapValues { (badgeId, badge) ->
            BadgeVersion(
                id = badgeId,
                title = badge.title,
                imageUrlLow = badge.imageUrlLow,
                imageUrlMedium = badge.imageUrlMedium,
                imageUrlHigh = badge.imageUrlHigh
            )
        }
    )
}