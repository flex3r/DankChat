package com.flxrs.dankchat.utils

import android.graphics.drawable.Drawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import pl.droidsonroids.gif.GifDrawable

class GifDrawableTarget(
		private val keyword: String,
		private val storeInCache: Boolean,
		private val start: Int = 0,
		private val end: Int = 0,
		private val callback: (Triple<Drawable, Int, Int>) -> Unit
) : CustomTarget<ByteArray>() {

	override fun onLoadStarted(placeholder: Drawable?) {
		if (placeholder != null) {
			callback(Triple(placeholder, start, end))
		}
	}

	override fun onLoadFailed(errorDrawable: Drawable?) {
		if (errorDrawable != null) {
			callback(Triple(errorDrawable, start, end))
		}
	}

	override fun onLoadCleared(placeholder: Drawable?) = Unit

	override fun onResourceReady(resource: ByteArray, transition: Transition<in ByteArray>?) {
		val drawable = GifDrawable(resource)
		if (storeInCache) {
			drawable.callback = EmoteManager.gifCallback
			EmoteManager.gifCache.put(keyword, drawable)
		}
		drawable.start()
		callback(Triple(drawable, start, end))
	}
}