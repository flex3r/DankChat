package com.flxrs.dankchat.preferences.tools.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.upload.UploadHostChecker
import com.flxrs.dankchat.preferences.tools.ImageUploaderConfig
import com.flxrs.dankchat.preferences.tools.ToolsSettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@KoinViewModel
class ImageUploaderViewModel(
    private val toolsSettingsDataStore: ToolsSettingsDataStore,
    uploadHostChecker: UploadHostChecker,
) : ViewModel() {
    private val uploadUrl = MutableStateFlow(toolsSettingsDataStore.current().uploaderConfig.uploadUrl)

    private val isHostUnresolvable =
        uploadUrl
            .debounce(HOST_CHECK_DEBOUNCE)
            .mapLatest { uploadHostChecker.isUnresolvable(it) }
            .onStart { emit(false) }

    val state =
        combine(toolsSettingsDataStore.uploadConfig, isHostUnresolvable, ::ImageUploaderState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5.seconds),
                initialValue = ImageUploaderState(uploaderConfig = toolsSettingsDataStore.current().uploaderConfig, isHostUnresolvable = false),
            )

    fun onUploadUrlChange(url: String) {
        uploadUrl.value = url
    }

    fun save(uploader: ImageUploaderConfig) = viewModelScope.launch {
        val validated =
            uploader.copy(
                headers = uploader.headers?.takeIf { it.isNotBlank() },
                imageLinkPattern = uploader.imageLinkPattern?.takeIf { it.isNotBlank() },
                deletionLinkPattern = uploader.deletionLinkPattern?.takeIf { it.isNotBlank() },
            )
        toolsSettingsDataStore.update { it.copy(uploaderConfig = validated) }
    }

    fun reset() = viewModelScope.launch {
        toolsSettingsDataStore.update { it.copy(uploaderConfig = ImageUploaderConfig.DEFAULT) }
    }

    companion object {
        private val HOST_CHECK_DEBOUNCE = 500.milliseconds
    }
}
