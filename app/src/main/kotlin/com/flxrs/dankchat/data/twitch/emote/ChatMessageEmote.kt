package com.flxrs.dankchat.data.twitch.emote

import android.os.Parcelable
import com.flxrs.dankchat.utils.IntRangeParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Parcelize
@TypeParceler<IntRange, IntRangeParceler>
data class ChatMessageEmote(
    val position: IntRange,
    val url: String,
    val id: String,
    val code: String,
    val scale: Int,
    val type: ChatMessageEmoteType,
    val isTwitch: Boolean = false,
    val isOverlayEmote: Boolean = false,
) : Parcelable
