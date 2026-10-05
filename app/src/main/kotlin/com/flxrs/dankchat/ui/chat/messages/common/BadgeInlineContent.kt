package com.flxrs.dankchat.ui.chat.messages.common

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.emote.EmoteAnimationCoordinator
import com.flxrs.dankchat.ui.chat.emote.EmoteDrawablePainter
import com.flxrs.dankchat.ui.chat.emote.LocalChatPageVisible
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val FfzVipShape = RoundedCornerShape(percent = 15)

@Composable
fun BadgeInlineContent(
    badge: BadgeUi,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shapedModifier =
        when (badge.badge) {
            is Badge.SharedChatBadge -> modifier.size(size).clip(CircleShape)
            is Badge.FFZVipBadge -> modifier.size(size).clip(FfzVipShape)
            else -> modifier.size(size)
        }
    val drawableResId = badge.drawableResId.takeIf { badge.badge !is Badge.FFZVipBadge }

    when (drawableResId) {
        null -> SharedBadgeImage(url = badge.url, size = size, contentDescription = badge.badge.type.name, modifier = shapedModifier)

        else -> AsyncImage(
            model = drawableResId,
            contentDescription = badge.badge.type.name,
            modifier = shapedModifier,
        )
    }
}

/**
 * Draws badges through the emote animation coordinator, so every occurrence of an animated badge
 * shares one drawable and stays in sync instead of animating its own copy.
 */
@Composable
private fun SharedBadgeImage(
    url: String,
    size: Dp,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val emoteCoordinator: EmoteAnimationCoordinator = koinInject()
    val sizePx = with(LocalDensity.current) { size.toPx().roundToInt() }
    val cacheKey = "$url\nbadge\n$sizePx"
    val isPageVisible = LocalChatPageVisible.current

    // Cache hits resolve synchronously, keyed by cacheKey so a reused slot never keeps another badge
    val drawableState = remember(cacheKey) { mutableStateOf(emoteCoordinator.getCached(cacheKey)) }
    LaunchedEffect(cacheKey, isPageVisible) {
        if (drawableState.value != null || !isPageVisible) {
            return@LaunchedEffect
        }

        drawableState.value =
            try {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(Size.ORIGINAL)
                        .build()
                context.imageLoader
                    .execute(request)
                    .image
                    ?.asDrawable(context.resources)
                    ?.fitInto(sizePx)
                    ?.also { emoteCoordinator.putInCache(cacheKey, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val drawable = drawableState.value ?: return@Box
        val painter = remember(drawable, isPageVisible) { EmoteDrawablePainter(drawable, emoteCoordinator, invalidationsEnabled = isPageVisible) }
        val density = LocalDensity.current
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier =
                with(density) {
                    Modifier.size(width = drawable.bounds.width().toDp(), height = drawable.bounds.height().toDp())
                },
        )
    }
}

// The painter draws at the drawable bounds, so they are fitted into the square badge slot
private fun Drawable.fitInto(sizePx: Int): Drawable {
    val largestSide = maxOf(intrinsicWidth, intrinsicHeight).coerceAtLeast(1)
    val scale = sizePx / largestSide.toFloat()
    setBounds(0, 0, (intrinsicWidth * scale).roundToInt(), (intrinsicHeight * scale).roundToInt())
    return this
}
