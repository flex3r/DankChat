package com.flxrs.dankchat.ui.chat.emote

import coil3.PlatformContext
import coil3.decode.StaticImageDecoder
import coil3.request.ImageRequest

private const val STILL_IMAGE_EXTRA = "still_image"

// Shared so all still decodes run under one parallelism limit
private val stillImageDecoderFactory = StaticImageDecoder.Factory()

/**
 * Image model for an emote shown outside of chat. Without animations the first frame is decoded as a still image,
 * cached apart from the animated image so turning animations back on does not hit the still one.
 */
fun emoteImageModel(
    context: PlatformContext,
    url: String,
    animate: Boolean,
): Any = when {
    animate -> url

    else ->
        ImageRequest
            .Builder(context)
            .data(url)
            .decoderFactory(stillImageDecoderFactory)
            .memoryCacheKeyExtra(STILL_IMAGE_EXTRA, "true")
            .build()
}
