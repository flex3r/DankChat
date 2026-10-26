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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import coil3.PlatformContext
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

// Badges drawn with a shape are clipped to it
internal val BadgeUi.shape: Shape?
    get() = when (badge) {
        is Badge.SharedChatBadge -> CircleShape
        is Badge.FFZVipBadge -> FfzVipShape
        else -> null
    }

private val BadgeUi.resourceId: Int?
    get() = drawableResId.takeIf { badge !is Badge.FFZVipBadge }

private fun BadgeUi.cacheKey(sizePx: Int): String = "${resourceId ?: url}\nbadge\n$sizePx"

internal fun EmoteAnimationCoordinator.getCachedBadge(
    badge: BadgeUi,
    sizePx: Int,
): Drawable? = getCached(badge.cacheKey(sizePx))

/**
 * Loads the badge fitted into its square slot. Every occurrence shares the cached drawable,
 * so animated badges stay in sync instead of animating their own copy.
 */
internal suspend fun EmoteAnimationCoordinator.loadBadge(
    context: PlatformContext,
    badge: BadgeUi,
    sizePx: Int,
): Drawable? {
    val key = badge.cacheKey(sizePx)
    getCached(key)?.let { return it }
    val drawable =
        try {
            when (val resourceId = badge.resourceId) {
                null -> {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(badge.url)
                            .size(Size.ORIGINAL)
                            .build()
                    context.imageLoader
                        .execute(request)
                        .image
                        ?.asDrawable(context.resources)
                }

                else -> {
                    ContextCompat.getDrawable(context, resourceId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    return drawable?.fitInto(sizePx)?.also { putInCache(key, it) }
}

@Composable
fun BadgeInlineContent(
    badge: BadgeUi,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shapedModifier =
        when (val shape = badge.shape) {
            null -> modifier.size(size)
            else -> modifier.size(size).clip(shape)
        }

    when (val resourceId = badge.resourceId) {
        null -> SharedBadgeImage(badge = badge, size = size, contentDescription = badge.badge.type.name, modifier = shapedModifier)

        else -> AsyncImage(
            model = resourceId,
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
    badge: BadgeUi,
    size: Dp,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val emoteCoordinator: EmoteAnimationCoordinator = koinInject()
    val sizePx = with(LocalDensity.current) { size.toPx().roundToInt() }
    val isPageVisible = LocalChatPageVisible.current

    // Cache hits resolve synchronously, keyed by badge and size so a reused slot never keeps another badge
    val drawableState = remember(badge, sizePx) { mutableStateOf(emoteCoordinator.getCachedBadge(badge, sizePx)) }
    LaunchedEffect(badge, sizePx, isPageVisible) {
        if (drawableState.value != null || !isPageVisible) {
            return@LaunchedEffect
        }
        drawableState.value = emoteCoordinator.loadBadge(context, badge, sizePx)
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
