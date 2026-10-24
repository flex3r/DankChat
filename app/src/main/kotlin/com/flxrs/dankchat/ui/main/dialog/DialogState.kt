package com.flxrs.dankchat.ui.main.dialog

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.data.repo.crash.CrashEntry

@Immutable
data class DialogState(
    val showAddChannel: Boolean = false,
    val showManageChannels: Boolean = false,
    val showRemoveChannel: Boolean = false,
    val showBlockChannel: Boolean = false,
    val showLogout: Boolean = false,
    val showNewWhisper: Boolean = false,
    val pendingUploadAction: UploadAction? = null,
    val isUploading: Boolean = false,
    val crashEntry: CrashEntry? = null,
)

enum class UploadAction {
    CaptureImage,
    CaptureVideo,
    ChooseMedia,
}
