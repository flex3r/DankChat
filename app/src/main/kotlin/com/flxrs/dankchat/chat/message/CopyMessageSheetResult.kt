package com.flxrs.dankchat.chat.message

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface CopyMessageSheetResult : Parcelable {
    @Parcelize
    data class Copy(val message: String) : CopyMessageSheetResult

    @Parcelize
    data class CopyId(val id: String) : CopyMessageSheetResult
}
