package com.flxrs.dankchat.data.twitch.emote

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.utils.IntRangeParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Immutable
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
    val cheerAmount: Int? = null,
    val cheerColor: Int? = null,
    // Half resolution tier of url, null when the emote has none or url is not the full resolution tier
    val lowResUrl: String? = null,
) : Parcelable
