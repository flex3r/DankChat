package com.flxrs.dankchat.ui.chat.emote

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.StaticImageDecoder
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.flxrs.dankchat.data.twitch.emote.GenericEmote

private const val STILL_IMAGE_EXTRA = "still_image"

// Shared so all still decodes run under one parallelism limit
private val stillImageDecoderFactory = StaticImageDecoder.Factory()

/**
 * Emote shown outside of chat. Files chat already loaded are shown from the disk cache first, a cached low resolution
 * file stays in place while the full resolution loads. Without cached files, an emote with a still image shows it first
 * and only then loads the animation, so the small still images are not queued behind large animations.
 */
@Composable
fun EmoteImage(
    emote: GenericEmote,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val cachedRequest = remember(emote.url, animate) { cachedEmoteRequest(context, emote.url, animate) }
    val cachedPainter = rememberAsyncImagePainter(cachedRequest)
    val cachedState by cachedPainter.state.collectAsState()
    when (cachedState) {
        is AsyncImagePainter.State.Error -> LowResCachedEmoteImage(emote, animate, modifier)
        else -> Image(painter = cachedPainter, contentDescription = emote.code, modifier = modifier)
    }
}

@Composable
private fun LowResCachedEmoteImage(
    emote: GenericEmote,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val lowResRequest = remember(emote.lowResUrl, animate) { cachedEmoteRequest(context, emote.lowResUrl, animate) }
    val lowResPainter = rememberAsyncImagePainter(lowResRequest)
    val lowResState by lowResPainter.state.collectAsState()
    when (val state = lowResState) {
        is AsyncImagePainter.State.Success -> {
            val model = remember(emote.url, animate) { emoteImageModel(context, emote.url, stillUrl = null, animate) }
            AsyncImage(
                model = model,
                contentDescription = emote.code,
                modifier = modifier,
                placeholder = state.painter,
                error = state.painter,
            )
        }

        is AsyncImagePainter.State.Error -> {
            NetworkEmoteImage(emote, animate, modifier)
        }

        else -> {
            Image(painter = lowResPainter, contentDescription = emote.code, modifier = modifier)
        }
    }
}

@Composable
private fun NetworkEmoteImage(
    emote: GenericEmote,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        animate && emote.stillUrl != null -> {
            StillFirstEmoteImage(emote.url, emote.stillUrl, emote.code, modifier)
        }

        else -> {
            val context = LocalPlatformContext.current
            val model = remember(emote.url, emote.stillUrl, animate) { emoteImageModel(context, emote.url, emote.stillUrl, animate) }
            AsyncImage(model = model, contentDescription = emote.code, modifier = modifier)
        }
    }
}

@Composable
private fun StillFirstEmoteImage(
    url: String,
    stillUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val stillPainter = rememberAsyncImagePainter(stillUrl)
    val stillState by stillPainter.state.collectAsState()
    when (val state = stillState) {
        is AsyncImagePainter.State.Success -> {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = modifier,
                placeholder = state.painter,
                error = state.painter,
            )
        }

        is AsyncImagePainter.State.Error -> {
            AsyncImage(model = url, contentDescription = contentDescription, modifier = modifier)
        }

        else -> {
            Image(painter = stillPainter, contentDescription = contentDescription, modifier = modifier)
        }
    }
}

// Fails without a network request when the file is not cached
private fun cachedEmoteRequest(
    context: PlatformContext,
    url: String,
    animate: Boolean,
): ImageRequest = ImageRequest
    .Builder(context)
    .data(url)
    .networkCachePolicy(CachePolicy.DISABLED)
    .apply {
        if (!animate) {
            stillImage()
        }
    }.build()

// Without animations the first frame is decoded as a still image, cached apart from the animated image
// so turning animations back on does not hit the still one
private fun emoteImageModel(
    context: PlatformContext,
    url: String,
    stillUrl: String?,
    animate: Boolean,
): Any = when {
    animate -> url

    stillUrl != null -> stillUrl

    else ->
        ImageRequest
            .Builder(context)
            .data(url)
            .stillImage()
            .build()
}

private fun ImageRequest.Builder.stillImage(): ImageRequest.Builder = decoderFactory(stillImageDecoderFactory)
    .memoryCacheKeyExtra(STILL_IMAGE_EXTRA, "true")
