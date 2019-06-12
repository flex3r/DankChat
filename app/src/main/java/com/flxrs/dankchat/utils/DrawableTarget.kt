package com.flxrs.dankchat.utils

import android.graphics.drawable.Drawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class DrawableTarget(private val callback: (Drawable) -> Unit) : CustomTarget<Drawable>() {

	override fun onLoadStarted(placeholder: Drawable?) {
		if (placeholder != null) {
			callback(placeholder)
		}
	}

	override fun onLoadFailed(errorDrawable: Drawable?) {
		if (errorDrawable != null) {
			callback(errorDrawable)
		}
	}

	override fun onLoadCleared(placeholder: Drawable?) = Unit

	override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
		callback(resource)
	}

}