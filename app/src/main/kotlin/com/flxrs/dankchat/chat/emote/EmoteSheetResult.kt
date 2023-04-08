package com.flxrs.dankchat.chat.emote

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface EmoteSheetResult : Parcelable {

    @Parcelize
    data class Use(val emoteName: String, val id: String) : EmoteSheetResult

    @Parcelize
    data class Copy(val emoteName: String) : EmoteSheetResult
}
