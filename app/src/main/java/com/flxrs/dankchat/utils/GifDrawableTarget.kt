package com.flxrs.dankchat.utils

import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import pl.droidsonroids.gif.GifDrawable

class GifDrawableTarget(private val keyword: String, private val callback: (ImageSpan) -> Unit) : CustomTarget<ByteArray>() {

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

	override fun onResourceReady(resource: ByteArray, transition: Transition<in ByteArray>?) {
		val drawable = GifDrawable(resource)
		drawable.callback = EmoteManager.gifCallback
		drawable.setBounds(0, 0, 112, 112)
		drawable.start()
		EmoteManager.gifCache.put(keyword, drawable)
		callback(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM))
	}
}