package com.flxrs.dankchat.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.flxrs.dankchat.service.twitch.emote.ChatEmote

class ImageSpanEmoteTarget(private val emote: ChatEmote, private val context: Context, private val callback: (ImageSpan) -> Unit) : CustomTarget<Bitmap>() {

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
		/* val resized = if (resource.height < 112 && emote.scale == 1) {
			val scaledWidth = (resource.width * 112) / resource.height
			Bitmap.createScaledBitmap(resource, scaledWidth, 112, true)
		} else {
			Bitmap.createScaledBitmap(resource, resource.width * emote.scale, resource.height * emote.scale, true)
		} */
		Bitmap.createScaledBitmap(resource, resource.width * emote.scale, resource.height * emote.scale, true).apply {
			callback(ImageSpan(context, this, ImageSpan.ALIGN_BOTTOM))
		}
	}
}