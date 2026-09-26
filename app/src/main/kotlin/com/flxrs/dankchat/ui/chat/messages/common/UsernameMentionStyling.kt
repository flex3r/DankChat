package com.flxrs.dankchat.ui.chat.messages.common

import androidx.compose.ui.graphics.Color

internal data class ResolvedUsernameMention(
    val start: Int,
    val end: Int,
    val color: Color?,
    val isBold: Boolean,
    val userAnnotation: String,
)

internal const val MENTIONED_USER_ANNOTATION_TAG = "MENTIONED_USER"
