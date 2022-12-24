package com.flxrs.dankchat.data.repo.data

import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName


sealed class DataLoadingStep {
    data object DankChatBadges : DataLoadingStep()
    data object GlobalBadges : DataLoadingStep()
    data object GlobalFFZEmotes : DataLoadingStep()
    data object GlobalBTTVEmotes : DataLoadingStep()
    data object GlobalSevenTVEmotes : DataLoadingStep()
    data class ChannelBadges(val channel: UserName, val channelId: UserId) : DataLoadingStep()
    data class ChannelFFZEmotes(val channel: UserName, val channelId: UserId) : DataLoadingStep()
    data class ChannelBTTVEmotes(val channel: UserName, val channelId: UserId) : DataLoadingStep()
    data class ChannelSevenTVEmotes(val channel: UserName, val channelId: UserId) : DataLoadingStep()
}
