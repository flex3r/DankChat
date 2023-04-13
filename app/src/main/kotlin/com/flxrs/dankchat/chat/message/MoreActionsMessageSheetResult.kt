package com.flxrs.dankchat.chat.message

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface MoreActionsMessageSheetResult : Parcelable {
    @Parcelize
    data class Copy(val message: String) : MoreActionsMessageSheetResult

    @Parcelize
    data class CopyId(val id: String) : MoreActionsMessageSheetResult
}
