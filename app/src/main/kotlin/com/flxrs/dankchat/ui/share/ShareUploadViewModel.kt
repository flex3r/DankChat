package com.flxrs.dankchat.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.TextResource
import com.flxrs.dankchat.utils.createMediaFile
import com.flxrs.dankchat.utils.extensions.runCatchingCancellable
import com.flxrs.dankchat.utils.removeExifAttributes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import java.io.File

@KoinViewModel
class ShareUploadViewModel(
    private val context: Context,
    private val dataRepository: DataRepository,
    private val dispatchersProvider: DispatchersProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val uri: Uri? = savedStateHandle[Intent.EXTRA_STREAM]
    private val _state = MutableStateFlow<ShareUploadState>(ShareUploadState.Loading)
    val state: StateFlow<ShareUploadState> = _state.asStateFlow()

    init {
        upload()
    }

    fun upload() = viewModelScope.launch {
        _state.value = ShareUploadState.Loading
        val file = uri?.let { copyToMediaFile(it) }
        if (file == null) {
            _state.value = ShareUploadState.Error(TextResource.Res(R.string.snackbar_upload_failed))
            return@launch
        }

        val result = withContext(dispatchersProvider.io) { dataRepository.uploadMedia(file) }
        _state.value =
            result.fold(
                onSuccess = { url -> ShareUploadState.Success(url) },
                onFailure = { error ->
                    val message = error.message?.let(TextResource::Plain) ?: TextResource.Res(R.string.snackbar_upload_failed)
                    ShareUploadState.Error(message)
                },
            )
    }

    private suspend fun copyToMediaFile(uri: Uri): File? = withContext(dispatchersProvider.io) {
        runCatchingCancellable {
            val mimeType = context.contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "tmp"
            val copy = createMediaFile(context, extension)
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { input.copyTo(it) }
            }
            if (copy.extension == "jpg" || copy.extension == "jpeg") {
                copy.removeExifAttributes()
            }
            copy
        }.getOrNull()
    }
}

@Immutable
sealed interface ShareUploadState {
    data object Loading : ShareUploadState

    data class Success(
        val url: String,
    ) : ShareUploadState

    data class Error(
        val message: TextResource,
    ) : ShareUploadState
}
