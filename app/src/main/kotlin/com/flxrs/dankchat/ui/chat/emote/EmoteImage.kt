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
import coil3.request.ImageRequest
import com.flxrs.dankchat.data.twitch.emote.GenericEmote

private const val STILL_IMAGE_EXTRA = "still_image"

// Shared so all still decodes run under one parallelism limit
private val stillImageDecoderFactory = StaticImageDecoder.Factory()

/**
 * Emote shown outside of chat. An emote with a still image shows it first and only then loads the animation,
 * so the small still images are not queued behind large animations. Without animations only the still image is loaded.
 */
@Composable
fun EmoteImage(
    emote: GenericEmote,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        animate && emote.stillUrl != null -> StillFirstEmoteImage(emote.url, emote.stillUrl, emote.code, modifier)

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
            .decoderFactory(stillImageDecoderFactory)
            .memoryCacheKeyExtra(STILL_IMAGE_EXTRA, "true")
            .build()
}
