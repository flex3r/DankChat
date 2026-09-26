package com.flxrs.dankchat.ui.chat.messages

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.asDrawable
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale
import com.flxrs.dankchat.data.twitch.message.toTwitchGifLoadUrl
import com.flxrs.dankchat.ui.chat.TwitchGifUi
import com.flxrs.dankchat.ui.chat.emote.LocalChatPageVisible
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject
import kotlin.math.min
import kotlin.math.roundToInt
import coil3.size.Size as CoilSize

private val TWITCH_GIF_MAX_HEIGHT = 140.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TwitchGifContent(
    gif: TwitchGifUi,
    fontSize: Float,
    fallbackColor: Color,
    animateGifs: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val isPageVisible = LocalChatPageVisible.current
    val gifCoordinator: TwitchGifCoordinator = koinInject()

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val maxHeightPx = with(density) { TWITCH_GIF_MAX_HEIGHT.roundToPx().coerceAtLeast(1) }
        val cacheKey = remember(gif.url, maxWidthPx, maxHeightPx) { "${gif.url}-$maxWidthPx-$maxHeightPx" }
        var state by remember(cacheKey) {
            mutableStateOf<TwitchGifLoadState>(
                gifCoordinator.get(cacheKey)?.let(TwitchGifLoadState::Loaded) ?: TwitchGifLoadState.Loading,
            )
        }

        LaunchedEffect(cacheKey, isPageVisible) {
            if (!isPageVisible || state is TwitchGifLoadState.Loaded) return@LaunchedEffect
            gifCoordinator.get(cacheKey)?.let { cached ->
                state = TwitchGifLoadState.Loaded(cached)
                return@LaunchedEffect
            }
            state = TwitchGifLoadState.Loading
            state =
                try {
                    val drawable =
                        gifCoordinator.getOrLoad(cacheKey) {
                            val renditionUrl = gif.url.toTwitchGifLoadUrl()
                            loadGifDrawable(context, renditionUrl, maxWidthPx, maxHeightPx)
                                ?: renditionUrl.takeIf { it != gif.url }?.let {
                                    loadGifDrawable(context, gif.url, maxWidthPx, maxHeightPx)
                                }
                        }
                    when (drawable) {
                        null -> TwitchGifLoadState.Failed
                        else -> TwitchGifLoadState.Loaded(drawable)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    TwitchGifLoadState.Failed
                }
        }

        when (val current = state) {
            TwitchGifLoadState.Loading -> {
                val placeholderSize = minOf(maxWidth, TWITCH_GIF_MAX_HEIGHT)
                Box(
                    Modifier
                        .size(placeholderSize, TWITCH_GIF_MAX_HEIGHT)
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        .semantics { contentDescription = gif.altText },
                ) {
                    Text(
                        text = gif.altText,
                        color = fallbackColor,
                        fontSize = fontSize.sp,
                    )
                }
            }

            TwitchGifLoadState.Failed -> {
                Text(
                    text = gif.altText,
                    color = fallbackColor,
                    fontSize = fontSize.sp,
                    modifier =
                        Modifier
                            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                            .semantics { contentDescription = gif.altText },
                )
            }

            is TwitchGifLoadState.Loaded -> {
                val drawable = current.drawable
                val sourceWidth = drawable.intrinsicWidth.coerceAtLeast(1)
                val sourceHeight = drawable.intrinsicHeight.coerceAtLeast(1)
                val scale = min(maxWidthPx.toFloat() / sourceWidth, maxHeightPx.toFloat() / sourceHeight)
                val widthPx = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
                val heightPx = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                drawable.setBounds(0, 0, widthPx, heightPx)

                val painter = remember(drawable, animateGifs, isPageVisible) {
                    TwitchGifDrawablePainter(
                        drawable = drawable,
                        gifCoordinator = gifCoordinator,
                        animate = animateGifs && isPageVisible,
                    )
                }
                Image(
                    painter = painter,
                    contentDescription = gif.altText,
                    modifier =
                        Modifier
                            .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                )
            }
        }
    }
}

private suspend fun loadGifDrawable(
    context: android.content.Context,
    url: String,
    maxWidthPx: Int,
    maxHeightPx: Int,
): Drawable? = try {
    val request =
        ImageRequest
            .Builder(context)
            .data(url)
            .size(CoilSize(maxWidthPx, maxHeightPx))
            .precision(Precision.INEXACT)
            .scale(Scale.FIT)
            .allowHardware(false)
            .build()
    context.imageLoader
        .execute(request)
        .image
        ?.asDrawable(context.resources)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

private sealed interface TwitchGifLoadState {
    data object Loading : TwitchGifLoadState

    data object Failed : TwitchGifLoadState

    data class Loaded(
        val drawable: Drawable,
    ) : TwitchGifLoadState
}

@Stable
private class TwitchGifDrawablePainter(
    private val drawable: Drawable,
    private val gifCoordinator: TwitchGifCoordinator,
    private val animate: Boolean,
) : Painter(),
    RememberObserver {
    private var invalidateTick by mutableIntStateOf(0)
    private val invalidationListener: () -> Unit = { invalidateTick++ }

    override val intrinsicSize: Size
        get() = Size(drawable.bounds.width().toFloat(), drawable.bounds.height().toFloat())

    override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean = false

    override fun DrawScope.onDraw() {
        invalidateTick
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }

    override fun onRemembered() {
        gifCoordinator.register(drawable, invalidationListener, animate)
    }

    override fun onForgotten() {
        gifCoordinator.unregister(drawable, invalidationListener)
    }

    override fun onAbandoned() = onForgotten()
}
