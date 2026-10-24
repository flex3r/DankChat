package com.flxrs.dankchat.ui.main

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flxrs.dankchat.data.api.ApiException
import com.flxrs.dankchat.data.repo.data.DataRepository
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.createMediaFile
import com.flxrs.dankchat.utils.extensions.runCatchingCancellable
import com.flxrs.dankchat.utils.removeExifAttributes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import java.io.File

@KoinViewModel
class MediaUploadViewModel(
    private val context: Context,
    private val dataRepository: DataRepository,
    private val mainEventBus: MainEventBus,
    private val dispatchersProvider: DispatchersProvider,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // The camera app can outlive the activity and the process, so the target file is kept in saved state
    fun createCaptureFile(extension: String): File? {
        val file = runCatching { createMediaFile(context, extension) }.getOrNull() ?: return null
        savedStateHandle[CAPTURE_FILE_KEY] = file.absolutePath
        return file
    }

    fun uploadCapture(imageCapture: Boolean) = viewModelScope.launch {
        val path = savedStateHandle.remove<String>(CAPTURE_FILE_KEY) ?: return@launch
        val file = File(path)
        mainEventBus.emitEvent(MainEvent.UploadLoading)
        if (imageCapture) {
            withContext(dispatchersProvider.io) { runCatching { file.removeExifAttributes() } }
        }
        upload(file)
    }

    fun uploadGalleryMedia(uri: Uri) = viewModelScope.launch {
        mainEventBus.emitEvent(MainEvent.UploadLoading)
        val copy = copyGalleryMedia(uri)
        if (copy == null) {
            mainEventBus.emitEvent(MainEvent.UploadFailed(errorMessage = null))
            return@launch
        }
        upload(copy)
    }

    private suspend fun copyGalleryMedia(uri: Uri): File? = withContext(dispatchersProvider.io) {
        val mimeType = context.contentResolver.getType(uri)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: return@withContext null
        val copy = runCatchingCancellable { createMediaFile(context, extension) }.getOrNull() ?: return@withContext null
        runCatchingCancellable {
            context.contentResolver.openInputStream(uri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
            if (copy.extension == "jpg" || copy.extension == "jpeg") {
                copy.removeExifAttributes()
            }
            copy
        }.onFailure { copy.delete() }.getOrNull()
    }

    private suspend fun upload(file: File) {
        val result = withContext(dispatchersProvider.io) { dataRepository.uploadMedia(file) }
        result.fold(
            onSuccess = { url ->
                file.delete()
                mainEventBus.emitEvent(MainEvent.UploadSuccess(url))
            },
            onFailure = { throwable ->
                val message =
                    when (throwable) {
                        is ApiException -> "${throwable.status} ${throwable.message}"
                        else -> throwable.message
                    }
                mainEventBus.emitEvent(MainEvent.UploadFailed(message))
            },
        )
    }

    companion object {
        private const val CAPTURE_FILE_KEY = "capture_file"
    }
}
