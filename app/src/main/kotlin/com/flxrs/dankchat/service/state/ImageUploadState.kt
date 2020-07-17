package com.flxrs.dankchat.service.state

import java.io.File

sealed class ImageUploadState {

    data class Loading(val mediaFile: File) : ImageUploadState()
    data class Finished(val url: String) : ImageUploadState()
    data class Failed(val errorMessage: String?, val mediaFile: File) : ImageUploadState()
}