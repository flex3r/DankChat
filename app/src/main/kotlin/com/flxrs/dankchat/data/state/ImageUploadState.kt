package com.flxrs.dankchat.data.state

import java.io.File

sealed class ImageUploadState {

    object None : ImageUploadState()
    object Loading : ImageUploadState()
    data class Finished(val url: String) : ImageUploadState()
    data class Failed(val errorMessage: String?, val mediaFile: File, val imageCapture: Boolean) : ImageUploadState()
}
