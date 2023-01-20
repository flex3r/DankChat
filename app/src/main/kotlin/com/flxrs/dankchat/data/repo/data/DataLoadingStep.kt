package com.flxrs.dankchat.data.repo.data

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName

sealed class DataLoadingStep {
    object DankChatBadges : DataLoadingStep() {
        override fun toString(): String = "DankChatBadges"
    }

    object GlobalBadges : DataLoadingStep() {
        override fun toString(): String = "GlobalBadges"
    }

    object GlobalFFZEmotes : DataLoadingStep() {
        override fun toString(): String = "GlobalFFZEmotes"
    }

    object GlobalBTTVEmotes : DataLoadingStep() {
        override fun toString(): String = "GlobalBTTVEmotes"
    }

    object GlobalSevenTVEmotes : DataLoadingStep() {
        override fun toString(): String = "GlobalSevenTVEmotes"
    }

    data class ChannelBadges(val channel: UserName, val channelId: UserId) : DataLoadingStep()
    data class ChannelFFZEmotes(val channel: UserName, val channelId: UserId) : DataLoadingStep()
    data class ChannelBTTVEmotes(val channel: UserName, val channelId: UserId) : DataLoadingStep()
    data class ChannelSevenTVEmotes(val channel: UserName, val channelId: UserId) : DataLoadingStep()
}
