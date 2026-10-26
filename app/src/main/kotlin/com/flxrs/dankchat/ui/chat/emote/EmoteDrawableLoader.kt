package com.flxrs.dankchat.ui.chat.emote

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asDrawable
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Size
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.ui.chat.EmoteUi
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

private const val BASE_HEIGHT_CONSTANT = 1.173
private const val SCALE_FACTOR_CONSTANT = 1.5 / 112

// Emotes are drawn at 4 * scaleFactor times their base size, the half resolution tier holds
// twice the base size and stays sharp up to this scale factor
private const val LOW_RES_MAX_SCALE_FACTOR = 0.5
private const val DISPLAY_SCALE_EXTRA = "emote_display_scale"

fun emoteBaseHeight(fontSizeSp: Float): Dp = (fontSizeSp * BASE_HEIGHT_CONSTANT).dp

internal fun emoteScaleFactor(baseHeightPx: Int): Double = baseHeightPx * SCALE_FACTOR_CONSTANT

// Cache key for a stacked emote's layer drawable and dimensions. Keyed by urls because
// emote ids are not unique across services (Twitch and FFZ ids share a numeric namespace).
internal fun EmoteUi.stackedCacheKey(baseHeightPx: Int): String = urls.joinToString(separator = "\n", postfix = "\n$baseHeightPx")

// Cache key for a single emote's drawable and dimensions, includes the render height
// because the drawable's bounds are baked for a specific font size at load time.
internal fun singleEmoteCacheKey(
    url: String,
    baseHeightPx: Int,
): String = "$url\n$baseHeightPx"

private val EmoteUi.isSingle: Boolean
    get() = urls.size == 1 && emotes.isNotEmpty()

// Cache key for the emote's drawable and dimensions at the given base height
internal fun EmoteUi.cacheKey(baseHeightPx: Int): String = when {
    isSingle -> singleEmoteCacheKey(urls.first(), baseHeightPx)
    else -> stackedCacheKey(baseHeightPx)
}

// Shown in place of an emote whose image failed to load
internal val EmoteUi.fallbackCode: String
    get() = when {
        isSingle -> emotes.first().code
        else -> code
    }

internal fun EmoteAnimationCoordinator.getCachedEmote(
    emote: EmoteUi,
    baseHeightPx: Int,
): Drawable? {
    val key = emote.cacheKey(baseHeightPx)
    return when {
        emote.isSingle -> getCached(key)
        else -> getLayerCached(key)
    }
}

/**
 * Loads the emote at the given base height, concurrent loads of the same emote share one load.
 * Stacks with failed layers render what loaded but are not cached, so the missing layers are retried on the next occurrence.
 */
internal suspend fun EmoteAnimationCoordinator.loadEmote(
    context: PlatformContext,
    emote: EmoteUi,
    baseHeightPx: Int,
): Drawable? {
    val key = emote.cacheKey(baseHeightPx)
    val scaleFactor = emoteScaleFactor(baseHeightPx)
    return when {
        emote.isSingle -> {
            loadShared(key) {
                context.loadEmoteDrawable(emote.emotes.first(), scaleFactor)?.also {
                    putInCache(key, it)
                    putDimensions(key, it.bounds.width() to it.bounds.height())
                }
            }
        }

        else -> {
            loadShared(key) {
                // Failed layers are skipped, every loaded drawable already carries its own display size
                val loadedLayers = emote.emotes.mapNotNull { context.loadEmoteDrawable(it, scaleFactor) }
                when {
                    loadedLayers.isEmpty() -> {
                        null
                    }

                    else -> {
                        loadedLayers.toTypedArray().toLayerDrawable().also {
                            val dimensions = it.bounds.width() to it.bounds.height()
                            when (loadedLayers.size) {
                                emote.urls.size -> {
                                    putLayerInCache(key, it)
                                    putDimensions(key, dimensions)
                                }

                                else -> {
                                    putPartialDimensions(key, dimensions)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class EmoteSource(
    val url: String,
    val tierScale: Int,
)

private fun ChatMessageEmote.sourceFor(scaleFactor: Double): EmoteSource = when {
    lowResUrl != null && scaleFactor <= LOW_RES_MAX_SCALE_FACTOR -> EmoteSource(lowResUrl, tierScale = 2)
    else -> EmoteSource(url, tierScale = 1)
}

/**
 * Loads the emote from the smallest tier that is still sharp at the given scale factor and decodes it
 * at its display size. The returned drawable has its bounds set to the display size, failures return null.
 */
private suspend fun PlatformContext.loadEmoteDrawable(
    emote: ChatMessageEmote,
    scaleFactor: Double,
): Drawable? = try {
    decodeEmoteDrawable(emote, scaleFactor)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

private suspend fun PlatformContext.decodeEmoteDrawable(
    emote: ChatMessageEmote,
    scaleFactor: Double,
): Drawable? {
    val source = emote.sourceFor(scaleFactor)
    val displaySize = { width: Int, height: Int -> emoteDisplaySize(width, height, scaleFactor, source.tierScale, emote) }
    val request =
        ImageRequest
            .Builder(this)
            .data(source.url)
            .size(Size.ORIGINAL)
            .decoderFactory(DisplaySizeDecoderFactory(displaySize))
            // Downscaled images only fit the display scale they were decoded for
            .memoryCacheKeyExtra(DISPLAY_SCALE_EXTRA, "${scaleFactor * source.tierScale * emote.scale}")
            .build()
    val result = imageLoader.execute(request) as? SuccessResult ?: return null
    val drawable = result.image.asDrawable(resources)
    val size =
        when {
            result.isSampled -> IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
            else -> displaySize(drawable.intrinsicWidth, drawable.intrinsicHeight)
        }
    drawable.setBounds(0, 0, size.width, size.height)
    return drawable
}

private fun emoteDisplaySize(
    width: Int,
    height: Int,
    scaleFactor: Double,
    tierScale: Int,
    emote: ChatMessageEmote,
): IntSize {
    val ratio = width / height.toFloat()
    val fullTierHeight = height * tierScale
    val displayHeight =
        when {
            fullTierHeight < 55 && emote.isTwitch -> (70 * scaleFactor).roundToInt()
            fullTierHeight in 55..111 && emote.isTwitch -> (112 * scaleFactor).roundToInt()
            else -> (fullTierHeight * scaleFactor).roundToInt()
        }
    val displayWidth = (displayHeight * ratio).roundToInt()
    return IntSize(displayWidth * emote.scale, displayHeight * emote.scale)
}

// Reads the source size from the image header and lets the regular decoders decode at the display size,
// images that are already smaller than that are decoded as they are
private class DisplaySizeDecoderFactory(
    private val displaySize: (width: Int, height: Int) -> IntSize,
) : Decoder.Factory {
    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        result.source
            .source()
            .peek()
            .inputStream()
            .use { BitmapFactory.decodeStream(it, null, bounds) }
        val target = displaySize.takeIf { bounds.outWidth > 0 && bounds.outHeight > 0 }?.invoke(bounds.outWidth, bounds.outHeight)
        val decodeOptions =
            when {
                target != null && target.width < bounds.outWidth && target.height < bounds.outHeight -> {
                    options.copy(size = Size(target.width, target.height), precision = Precision.EXACT)
                }

                else -> {
                    options
                }
            }
        return imageLoader.components.newDecoder(result, decodeOptions, imageLoader)?.first
    }
}

private fun Array<Drawable>.toLayerDrawable(): LayerDrawable {
    // Captured first, setting the layer bounds resizes every layer to them
    val sizes = map { IntSize(it.bounds.width(), it.bounds.height()) }
    val maxWidth = sizes.maxOf { it.width }
    val maxHeight = sizes.maxOf { it.height }
    return LayerDrawable(this).apply {
        setBounds(0, 0, maxWidth, maxHeight)
        // Layers are centered horizontally and aligned to the bottom
        this@toLayerDrawable.forEachIndexed { index, drawable ->
            val size = sizes[index]
            val left = ((maxWidth - size.width) / 2).coerceAtLeast(0)
            val top = (maxHeight - size.height).coerceAtLeast(0)
            drawable.setBounds(left, top, left + size.width, top + size.height)
        }
    }
}
