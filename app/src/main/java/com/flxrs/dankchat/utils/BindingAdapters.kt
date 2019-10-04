package com.flxrs.dankchat.utils

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.flxrs.dankchat.R

@BindingAdapter("url")
fun ImageView.loadImage(url: String) {
    Glide.with(this)
        .load(url)
        .placeholder(R.drawable.ic_missing_emote)
        .error(R.drawable.ic_missing_emote)
        .into(this)
}
