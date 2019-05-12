package com.flxrs.dankchat.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class ImageSpanBadgeTarget(private val context: Context, private val callback: (ImageSpan) -> Unit) : CustomTarget<Bitmap>() {

	override fun onLoadStarted(placeholder: Drawable?) {
		if (placeholder != null) {
			callback(ImageSpan(placeholder, ImageSpan.ALIGN_BOTTOM))
		}
	}

	override fun onLoadFailed(errorDrawable: Drawable?) {
		if (errorDrawable != null) {
			callback(ImageSpan(errorDrawable, ImageSpan.ALIGN_BOTTOM))
		}
	}

	override fun onLoadCleared(placeholder: Drawable?) = Unit

	override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
		callback(ImageSpan(context, resource, ImageSpan.ALIGN_BOTTOM))
	}
}