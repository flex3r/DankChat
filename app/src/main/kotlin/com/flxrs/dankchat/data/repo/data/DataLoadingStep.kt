package com.flxrs.dankchat.data.repo.data

import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.utils.extensions.partitionIsInstance

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
    data class ChannelBTTVEmotes(val channel: UserName, val channelDisplayName: DisplayName, val channelId: UserId) : DataLoadingStep()
    data class ChannelSevenTVEmotes(val channel: UserName, val channelId: UserId) : DataLoadingStep()
}

fun List<DataLoadingStep>.toMergedStrings(): List<String> {
    val (badges, notBadges) = partitionIsInstance<DataLoadingStep.ChannelBadges, _>()
    val (ffz, notFfz) = notBadges.partitionIsInstance<DataLoadingStep.ChannelFFZEmotes, _>()
    val (bttv, notBttv) = notFfz.partitionIsInstance<DataLoadingStep.ChannelBTTVEmotes, _>()
    val (sevenTv, rest) = notBttv.partitionIsInstance<DataLoadingStep.ChannelSevenTVEmotes, _>()

    return buildList {
        addAll(rest.map(DataLoadingStep::toString))

        if (badges.isNotEmpty()) {
            add("ChannelBadges(${badges.joinToString(separator = ",") { it.channel.value }})")
        }
        if (ffz.isNotEmpty()) {
            add("ChannelFFZEmotes(${ffz.joinToString(separator = ",") { it.channel.value }})")
        }
        if (bttv.isNotEmpty()) {
            add("ChannelBTTVEmotes(${bttv.joinToString(separator = ",") { it.channel.value }})")
        }
        if (sevenTv.isNotEmpty()) {
            add("ChannelSevenTVEmotes(${sevenTv.joinToString(separator = ",") { it.channel.value }})")
        }
    }
}


