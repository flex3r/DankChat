package com.flxrs.dankchat.service.api.dto

import java.time.Instant

data class UploadDto(
    val imageLink: String,
    val deleteLink: String?,
    val timestamp: Instant
)