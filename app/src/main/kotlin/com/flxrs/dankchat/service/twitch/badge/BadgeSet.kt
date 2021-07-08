package com.flxrs.dankchat.service.twitch.badge

import com.flxrs.dankchat.service.api.dto.HelixBadgesDto
import com.flxrs.dankchat.service.api.dto.TwitchBadgesDto

data class BadgeSet(
    val id: String,
    val versions: Map<String, BadgeVersion>
)

data class BadgeVersion(
    val id: String,
    val imageUrlLow: String,
    val imageUrlMedium: String,
    val imageUrlHigh: String
)

fun HelixBadgesDto.toBadgeSets(): Map<String, BadgeSet> = sets.map { set ->
    set.setId to BadgeSet(
        id = set.setId,
        versions = set.versions.map { badge ->
            badge.badgeId to BadgeVersion(
                id = badge.badgeId,
                imageUrlLow = badge.imageUrlLow,
                imageUrlMedium = badge.imageUrlMedium,
                imageUrlHigh = badge.imageUrlHigh
            )
        }.toMap()
    )
}.toMap()

fun TwitchBadgesDto.toBadgeSets(): Map<String, BadgeSet> = sets.mapValues { (id, set) ->
    BadgeSet(
        id = id,
        versions = set.versions.mapValues { (badgeId, badge) ->
            BadgeVersion(
                id = badgeId,
                imageUrlLow = badge.imageUrlLow,
                imageUrlMedium = badge.imageUrlMedium,
                imageUrlHigh = badge.imageUrlHigh
            )
        }
    )
}