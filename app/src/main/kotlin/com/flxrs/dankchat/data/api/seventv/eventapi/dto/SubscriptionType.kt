package com.flxrs.dankchat.data.api.seventv.eventapi.dto

enum class SubscriptionType(val type: String) {
    UserUpdates(type = "user.update"),
    EmoteSetUpdates(type = "emote_set.update"),
}
