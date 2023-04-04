package com.flxrs.dankchat.chat.message

import android.os.Parcelable
import com.flxrs.dankchat.data.UserName
import kotlinx.parcelize.Parcelize

sealed interface MessageSheetResult : Parcelable {
    @Parcelize
    data class Copy(val message: String) : MessageSheetResult

    @Parcelize
    data class CopyId(val id: String) : MessageSheetResult

    @Parcelize
    data class Reply(val replyMessageId: String, val replyName: UserName) : MessageSheetResult

    @Parcelize
    data class ViewThread(val replyMessageId: String) : MessageSheetResult
}
