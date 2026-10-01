package com.flxrs.dankchat.ui.chat.emote

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.asDrawable
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.ui.chat.EmoteUi
import com.flxrs.dankchat.utils.extensions.forEachLayer
import com.flxrs.dankchat.utils.extensions.setRunning
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

private const val BASE_HEIGHT_CONSTANT = 1.173
private const val SCALE_FACTOR_CONSTANT = 1.5 / 112

fun emoteBaseHeight(fontSizeSp: Float): Dp = (fontSizeSp * BASE_HEIGHT_CONSTANT).dp

internal fun emoteScaleFactor(baseHeightPx: Int): Double = baseHeightPx * SCALE_FACTOR_CONSTANT

// Cache key for a stacked emote's layer drawable and dimensions. Keyed by urls because
// emote ids are not unique across services (Twitch and FFZ ids share a numeric namespace).
internal fun EmoteUi.stackedCacheKey(baseHeightPx: Int): String = urls.joinToString(separator = "\n", postfix = "\n$baseHeightPx")

// Cache key for a single emote's drawable and dimensions — includes the render height
// because the drawable's bounds are baked for a specific font size at load time.
internal fun singleEmoteCacheKey(
    url: String,
    baseHeightPx: Int,
): String = "$url\n$baseHeightPx"

@Composable
fun StackedEmote(
    emote: EmoteUi,
    fontSize: Float,
    emoteCoordinator: EmoteAnimationCoordinator,
    modifier: Modifier = Modifier,
    animateGifs: Boolean = true,
    alpha: Float = 1f,
    onClick: () -> Unit = {},
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val baseHeight = emoteBaseHeight(fontSize)
    val baseHeightPx = with(density) { baseHeight.toPx().toInt() }
    val scaleFactor = emoteScaleFactor(baseHeightPx)

    // For single emote, render directly without LayerDrawable
    if (emote.urls.size == 1 && emote.emotes.isNotEmpty()) {
        SingleEmoteDrawable(
            url = emote.urls.first(),
            chatEmote = emote.emotes.first(),
            fontSize = fontSize,
            scaleFactor = scaleFactor,
            emoteCoordinator = emoteCoordinator,
            animateGifs = animateGifs,
            alpha = alpha,
            modifier = modifier,
            onClick = onClick,
        )
        return
    }

    val cacheKey = emote.stackedCacheKey(baseHeightPx)

    // Estimate placeholder size from dimension cache or from base height
    val cachedDims = emoteCoordinator.getDimensions(cacheKey)
    val estimatedHeightPx = cachedDims?.second ?: (baseHeightPx * (emote.emotes.firstOrNull()?.scale ?: 1))
    val estimatedWidthPx = cachedDims?.first ?: estimatedHeightPx

    // Load or create LayerDrawable asynchronously, cache hits resolve synchronously so the
    // first frame already renders the emote. The state is keyed by cacheKey so a composition
    // slot reused for a different emote can never keep the previous emote's drawable.
    val isPageVisible = LocalChatPageVisible.current
    val layerDrawableState =
        remember(cacheKey) {
            mutableStateOf(emoteCoordinator.getLayerCached(cacheKey)?.let(EmoteLoadState::Loaded) ?: EmoteLoadState.Loading)
        }
    LaunchedEffect(cacheKey, isPageVisible) {
        if (layerDrawableState.value is EmoteLoadState.Loaded) {
            return@LaunchedEffect
        }

        // Check cache first
        val cached = emoteCoordinator.getLayerCached(cacheKey)
        if (cached != null) {
            layerDrawableState.value = EmoteLoadState.Loaded(cached)
            // Control animation
            cached.forEachLayer<Animatable> { it.setRunning(animateGifs) }
            return@LaunchedEffect
        }

        if (!isPageVisible) {
            return@LaunchedEffect
        }

        // Load all drawables, keeping each one paired with its emote so a failed layer
        // cannot misalign the remaining layers
        val loadedLayers =
            emote.urls.mapIndexedNotNull { idx, url ->
                val emoteData = emote.emotes.getOrNull(idx) ?: emote.emotes.first()
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(Size.ORIGINAL)
                            .build()
                    val result = context.imageLoader.execute(request)
                    result.image?.asDrawable(context.resources)?.let { drawable ->
                        transformEmoteDrawable(drawable, scaleFactor, emoteData) to emoteData
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

        when {
            loadedLayers.isEmpty() -> {
                layerDrawableState.value = EmoteLoadState.Failed
            }

            else -> {
                val drawables = loadedLayers.map { it.first }.toTypedArray()
                val layerDrawable = drawables.toLayerDrawable(scaleFactor, loadedLayers.map { it.second })
                // Partial stacks render what loaded but must not be cached, otherwise the
                // missing layers would never be retried within the session
                if (loadedLayers.size == emote.urls.size) {
                    emoteCoordinator.putLayerInCache(cacheKey, layerDrawable)
                    // Store dimensions for future placeholder sizing
                    emoteCoordinator.putDimensions(
                        cacheKey,
                        layerDrawable.bounds.width() to layerDrawable.bounds.height(),
                    )
                }
                layerDrawableState.value = EmoteLoadState.Loaded(layerDrawable)
                // Control animation
                layerDrawable.forEachLayer<Animatable> { it.setRunning(animateGifs) }
            }
        }
    }

    // Update animation state when setting changes
    SideEffect(animateGifs, layerDrawableState.value) {
        val loaded = layerDrawableState.value as? EmoteLoadState.Loaded ?: return@SideEffect
        (loaded.drawable as? LayerDrawable)?.forEachLayer<Animatable> { it.setRunning(animateGifs) }
    }

    when (val state = layerDrawableState.value) {
        is EmoteLoadState.Loaded -> {
            // Render with actual dimensions
            val widthDp = with(density) {
                state.drawable.bounds
                    .width()
                    .toDp()
            }
            val heightDp = with(density) {
                state.drawable.bounds
                    .height()
                    .toDp()
            }
            val painter = remember(state.drawable, isPageVisible) { EmoteDrawablePainter(state.drawable, emoteCoordinator, invalidationsEnabled = isPageVisible) }

            Image(
                painter = painter,
                contentDescription = null,
                alpha = alpha,
                modifier =
                    modifier
                        .size(width = widthDp, height = heightDp)
                        .clickable { onClick() },
            )
        }

        EmoteLoadState.Failed -> {
            EmoteCodeFallback(
                code = emote.code,
                fontSize = fontSize,
                alpha = alpha,
                cacheKey = cacheKey,
                emoteCoordinator = emoteCoordinator,
                modifier = modifier,
                onClick = onClick,
            )
        }

        EmoteLoadState.Loading -> {
            // Placeholder with estimated size to prevent layout shift
            val widthDp = with(density) { estimatedWidthPx.toDp() }
            val heightDp = with(density) { estimatedHeightPx.toDp() }
            Box(
                modifier =
                    modifier
                        .size(width = widthDp, height = heightDp)
                        .clickable { onClick() },
            )
        }
    }
}

@Composable
private fun SingleEmoteDrawable(
    url: String,
    chatEmote: ChatMessageEmote,
    fontSize: Float,
    scaleFactor: Double,
    emoteCoordinator: EmoteAnimationCoordinator,
    animateGifs: Boolean,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    onClick: () -> Unit = {},
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val baseHeightPx = with(density) { emoteBaseHeight(fontSize).toPx().toInt() }
    val cacheKey = singleEmoteCacheKey(url, baseHeightPx)

    // Use dimension cache for instant placeholder sizing on repeat views
    val cachedDims = emoteCoordinator.getDimensions(cacheKey)

    // Load drawable asynchronously, cache hits resolve synchronously so the first frame
    // already renders the emote. The state is keyed by cacheKey so a composition slot reused
    // for a different emote can never keep the previous emote's drawable.
    val isPageVisible = LocalChatPageVisible.current
    val drawableState =
        remember(cacheKey) {
            mutableStateOf(emoteCoordinator.getCached(cacheKey)?.let(EmoteLoadState::Loaded) ?: EmoteLoadState.Loading)
        }
    LaunchedEffect(cacheKey, isPageVisible) {
        if (drawableState.value is EmoteLoadState.Loaded) {
            return@LaunchedEffect
        }

        // Fast path: check cache first
        val cached = emoteCoordinator.getCached(cacheKey)
        if (cached != null) {
            drawableState.value = EmoteLoadState.Loaded(cached)
            return@LaunchedEffect
        }

        if (!isPageVisible) {
            return@LaunchedEffect
        }

        val transformed =
            try {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(Size.ORIGINAL)
                        .build()
                val result = context.imageLoader.execute(request)
                result.image?.asDrawable(context.resources)?.let { drawable ->
                    // Transform and cache
                    transformEmoteDrawable(drawable, scaleFactor, chatEmote).also {
                        emoteCoordinator.putInCache(cacheKey, it)
                        // Store dimensions for future placeholder sizing
                        emoteCoordinator.putDimensions(
                            cacheKey,
                            it.bounds.width() to it.bounds.height(),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        drawableState.value = when (transformed) {
            null -> EmoteLoadState.Failed
            else -> EmoteLoadState.Loaded(transformed)
        }
    }

    // Update animation state when setting changes
    SideEffect(animateGifs, drawableState.value) {
        val loaded = drawableState.value as? EmoteLoadState.Loaded ?: return@SideEffect
        (loaded.drawable as? Animatable)?.setRunning(animateGifs)
    }

    when (val state = drawableState.value) {
        is EmoteLoadState.Loaded -> {
            // Render with actual dimensions
            val widthDp = with(density) {
                state.drawable.bounds
                    .width()
                    .toDp()
            }
            val heightDp = with(density) {
                state.drawable.bounds
                    .height()
                    .toDp()
            }
            val painter = remember(state.drawable, isPageVisible) { EmoteDrawablePainter(state.drawable, emoteCoordinator, invalidationsEnabled = isPageVisible) }

            Image(
                painter = painter,
                contentDescription = null,
                alpha = alpha,
                modifier =
                    modifier
                        .size(width = widthDp, height = heightDp)
                        .clickable { onClick() },
            )
        }

        EmoteLoadState.Failed -> {
            EmoteCodeFallback(
                code = chatEmote.code,
                fontSize = fontSize,
                alpha = alpha,
                cacheKey = cacheKey,
                emoteCoordinator = emoteCoordinator,
                modifier = modifier,
                onClick = onClick,
            )
        }

        EmoteLoadState.Loading -> {
            // Placeholder keeps the inline slot measurable while loading — emitting nothing
            // would drop the emote from the inline content list and shift sibling slots
            val estimatedSizePx = baseHeightPx * chatEmote.scale
            val widthDp = with(density) { (cachedDims?.first ?: estimatedSizePx).toDp() }
            val heightDp = with(density) { (cachedDims?.second ?: estimatedSizePx).toDp() }
            Box(
                modifier =
                    modifier
                        .size(width = widthDp, height = heightDp)
                        .clickable { onClick() },
            )
        }
    }
}

// Shown in place of an emote whose image failed to load
@Composable
private fun EmoteCodeFallback(
    code: String,
    fontSize: Float,
    alpha: Float,
    cacheKey: String,
    emoteCoordinator: EmoteAnimationCoordinator,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    // Report the fallback text size so waiting rows resize this emote's inline placeholder
    val textMeasurer = rememberTextMeasurer()
    val textStyle = LocalTextStyle.current
    SideEffect(cacheKey, fontSize) {
        val size = textMeasurer.measure(AnnotatedString(code), textStyle.copy(fontSize = fontSize.sp)).size
        emoteCoordinator.putDimensions(cacheKey, size.width to size.height)
    }
    Text(
        text = code,
        fontSize = fontSize.sp,
        modifier =
            modifier
                .alpha(alpha)
                .clickable { onClick() },
    )
}

private sealed interface EmoteLoadState {
    data object Loading : EmoteLoadState

    data object Failed : EmoteLoadState

    data class Loaded(
        val drawable: Drawable,
    ) : EmoteLoadState
}

private fun transformEmoteDrawable(
    drawable: Drawable,
    scale: Double,
    emote: ChatMessageEmote,
    maxWidth: Int = 0,
    maxHeight: Int = 0,
): Drawable {
    val ratio = drawable.intrinsicWidth / drawable.intrinsicHeight.toFloat()
    val height =
        when {
            drawable.intrinsicHeight < 55 && emote.isTwitch -> (70 * scale).roundToInt()
            drawable.intrinsicHeight in 55..111 && emote.isTwitch -> (112 * scale).roundToInt()
            else -> (drawable.intrinsicHeight * scale).roundToInt()
        }
    val width = (height * ratio).roundToInt()

    val scaledWidth = width * emote.scale
    val scaledHeight = height * emote.scale

    val left = if (maxWidth > 0) (maxWidth - scaledWidth).div(2).coerceAtLeast(0) else 0
    val top = (maxHeight - scaledHeight).coerceAtLeast(0)

    drawable.setBounds(left, top, scaledWidth + left, scaledHeight + top)
    return drawable
}

private fun Array<Drawable>.toLayerDrawable(
    scaleFactor: Double,
    emotes: List<ChatMessageEmote>,
): LayerDrawable = LayerDrawable(this).apply {
    val bounds = this@toLayerDrawable.map { it.bounds }
    val maxWidth = bounds.maxOf { it.width() }
    val maxHeight = bounds.maxOf { it.height() }
    setBounds(0, 0, maxWidth, maxHeight)

    // Phase 2: Re-adjust bounds with maxWidth/maxHeight
    forEachIndexed { idx, dr ->
        transformEmoteDrawable(dr, scaleFactor, emotes[idx], maxWidth, maxHeight)
    }
}
