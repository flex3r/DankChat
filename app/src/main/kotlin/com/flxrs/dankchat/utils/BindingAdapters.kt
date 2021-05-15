package com.flxrs.dankchat.utils

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import coil.load
import coil.size.Scale
import com.flxrs.dankchat.R

@BindingAdapter("url")
fun ImageView.loadImage(url: String) {
    load(url) {
        scale(Scale.FILL)
        placeholder(R.drawable.ic_missing_emote)
        error(R.drawable.ic_missing_emote)
    }
}
