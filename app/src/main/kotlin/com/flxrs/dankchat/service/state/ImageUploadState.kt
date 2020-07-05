package com.flxrs.dankchat.service.state

sealed class ImageUploadState {

    object Loading : ImageUploadState()
    data class Finished(val url: String) : ImageUploadState()
    data class Failed(val errorMessage: String?) : ImageUploadState()
}