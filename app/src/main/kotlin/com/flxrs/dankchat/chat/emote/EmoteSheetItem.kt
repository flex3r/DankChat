package com.flxrs.dankchat.chat.emote

import androidx.annotation.StringRes
import com.flxrs.dankchat.data.DisplayName

data class EmoteSheetItem(
    val id: String,
    val name: String,
    val baseName: String?,
    val imageUrl: String,
    @StringRes val emoteType: Int,
    val providerUrl: String,
    val isZeroWidth: Boolean,
    val creatorName: DisplayName?,
)
