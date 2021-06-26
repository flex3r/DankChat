package com.flxrs.dankchat.utils.extensions

import android.view.View
import android.widget.ImageView
import coil.load
import coil.request.ImageRequest
import com.flxrs.dankchat.R
import com.google.android.material.snackbar.Snackbar

fun View.showShortSnackbar(text: String, block: Snackbar.() -> Unit = {}) = Snackbar.make(this, text, Snackbar.LENGTH_SHORT)
    .apply(block)
    .show()

fun View.showLongSnackbar(text: String, block: Snackbar.() -> Unit = {}) = Snackbar.make(this, text, Snackbar.LENGTH_LONG)
    .apply(block)
    .show()

inline fun ImageView.loadImage(url: String, block: ImageRequest.Builder.() -> Unit = {}) {
    load(url) {
        block()
        placeholder(R.drawable.ic_missing_emote)
        error(R.drawable.ic_missing_emote)
    }
}