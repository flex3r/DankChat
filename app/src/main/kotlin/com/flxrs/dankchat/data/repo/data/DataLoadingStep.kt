package com.flxrs.dankchat.data.repo.data

sealed class DataLoadingStep {
    data object DankChatBadges : DataLoadingStep()
    data object GlobalBadges : DataLoadingStep()
    data object GlobalFFZEmotes : DataLoadingStep()
    data object GlobalBTTVEmotes : DataLoadingStep()
    data object GlobalSevenTVEmotes : DataLoadingStep()
    data class ChannelBadges(val channel: String, val channelId: String) : DataLoadingStep()
    data class ChannelFFZEmotes(val channel: String, val channelId: String) : DataLoadingStep()
    data class ChannelBTTVEmotes(val channel: String, val channelId: String) : DataLoadingStep()
    data class ChannelSevenTVEmotes(val channel: String, val channelId: String) : DataLoadingStep()
}
