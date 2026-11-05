package com.flxrs.dankchat.preferences.tools.upload

import androidx.compose.runtime.Immutable
import com.flxrs.dankchat.preferences.tools.ImageUploaderConfig

@Immutable
data class ImageUploaderState(
    val uploaderConfig: ImageUploaderConfig,
    val isHostUnresolvable: Boolean,
)
