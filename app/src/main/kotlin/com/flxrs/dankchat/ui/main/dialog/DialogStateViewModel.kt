package com.flxrs.dankchat.ui.main.dialog

import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.data.repo.crash.CrashRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DialogStateViewModel(
    private val preferenceStore: DankChatPreferenceStore,
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
    private val crashRepository: CrashRepository,
    developerSettingsDataStore: DeveloperSettingsDataStore,
) : ViewModel() {
    private val _state = MutableStateFlow(DialogState())
    val state: StateFlow<DialogState> = _state.asStateFlow()

    init {
        val debugMode = developerSettingsDataStore.current().debugMode
        if (debugMode && crashRepository.hasUnshownCrash()) {
            val crash = crashRepository.getMostRecentCrash()
            if (crash != null) {
                update { copy(crashEntry = crash) }
            }
            crashRepository.markCrashShown()
        }
    }

    // Channel dialogs
    fun showAddChannel() {
        update { copy(showAddChannel = true) }
    }

    fun dismissAddChannel() {
        update { copy(showAddChannel = false) }
    }

    fun showManageChannels() {
        update { copy(showManageChannels = true) }
    }

    fun dismissManageChannels() {
        update { copy(showManageChannels = false) }
    }

    fun showRemoveChannel() {
        update { copy(showRemoveChannel = true) }
    }

    fun dismissRemoveChannel() {
        update { copy(showRemoveChannel = false) }
    }

    fun showBlockChannel() {
        update { copy(showBlockChannel = true) }
    }

    fun dismissBlockChannel() {
        update { copy(showBlockChannel = false) }
    }

    // Auth dialogs
    fun showLogout() {
        update { copy(showLogout = true) }
    }

    fun dismissLogout() {
        update { copy(showLogout = false) }
    }

    // Whisper dialog
    fun showNewWhisper() {
        update { copy(showNewWhisper = true) }
    }

    fun dismissNewWhisper() {
        update { copy(showNewWhisper = false) }
    }

    // Upload
    val uploadHost: String
        get() =
            runCatching {
                java.net.URL(toolsSettingsDataStore.current().uploaderConfig.uploadUrl).host
            }.getOrDefault("")

    fun setPendingUploadAction(action: UploadAction?) {
        update { copy(pendingUploadAction = action) }
    }

    fun acknowledgeExternalHosting() {
        preferenceStore.hasExternalHostingAcknowledged = true
    }

    fun setUploading(uploading: Boolean) {
        update { copy(isUploading = uploading) }
    }

    // Crash report
    fun dismissCrashReport() {
        update { copy(crashEntry = null) }
    }

    fun getCrashReportMessage(): String? {
        val entry = _state.value.crashEntry ?: return null
        return crashRepository.buildCrashReportMessage(entry)
    }

    private inline fun update(crossinline transform: DialogState.() -> DialogState) {
        _state.value = _state.value.transform()
    }
}
