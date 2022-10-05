package com.flxrs.dankchat.utils.extensions

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.text.style.TypefaceSpan
import androidx.core.text.inSpans
import com.flxrs.dankchat.R

inline fun SpannableStringBuilder.timestampFont(
    context: Context, // this is required just because we need to retreive the R.style stuff
    builderAction: SpannableStringBuilder.() -> Unit
): SpannableStringBuilder = inSpans(
    TypefaceSpan("monospace"),
    StyleSpan(Typeface.BOLD),
    // style adjustments to make the monospaced text looks "same size" as the normal text
    RelativeSizeSpan(0.95f),
    TextAppearanceSpan(context, R.style.timestamp_and_whisper), // set letter spacing using this, can't set directly in code
    builderAction = builderAction
)
