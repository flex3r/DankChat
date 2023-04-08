package com.flxrs.dankchat.data.twitch.badge

import com.flxrs.dankchat.data.api.badges.dto.TwitchBadgeSetsDto
import com.flxrs.dankchat.data.api.helix.dto.BadgeSetDto

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

fun List<BadgeSetDto>.toBadgeSets(): Map<String, BadgeSet> = associate { (id, versions) ->
    id to BadgeSet(
        id = id,
        versions = versions.associate { badge ->
            badge.id to BadgeVersion(
                id = badge.id,
                title = badge.title,
                imageUrlLow = badge.imageUrlLow,
                imageUrlMedium = badge.imageUrlMedium,
                imageUrlHigh = badge.imageUrlHigh
            )
        }
    )
}
