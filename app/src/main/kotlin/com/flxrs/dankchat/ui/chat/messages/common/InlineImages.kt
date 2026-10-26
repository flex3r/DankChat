package com.flxrs.dankchat.ui.chat.messages.common

import android.graphics.drawable.Drawable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.EmoteUi
import com.flxrs.dankchat.ui.chat.emote.EmoteAnimationCoordinator
import com.flxrs.dankchat.ui.chat.emote.LocalChatPageVisible
import com.flxrs.dankchat.ui.chat.emote.LocalEmoteAnimationCoordinator
import com.flxrs.dankchat.ui.chat.emote.cacheKey
import com.flxrs.dankchat.ui.chat.emote.emoteBaseHeight
import com.flxrs.dankchat.ui.chat.emote.fallbackCode
import com.flxrs.dankchat.ui.chat.emote.getCachedEmote
import com.flxrs.dankchat.ui.chat.emote.loadEmote
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

internal fun badgeInlineId(badge: BadgeUi): String = "BADGE_${badge.position}"

internal fun emoteInlineId(emote: EmoteUi): String = "EMOTE_${emote.position}"

/** Loads the badges and emotes of a message text and keeps their animations running while the page is visible. */
@Composable
internal fun rememberInlineImages(
    badges: List<BadgeUi>,
    emotes: List<EmoteUi>,
    fontSize: Float,
): InlineImages {
    val context = LocalPlatformContext.current
    val emoteCoordinator = LocalEmoteAnimationCoordinator.current
    val isPageVisible = LocalChatPageVisible.current
    val baseHeightPx = with(LocalDensity.current) { emoteBaseHeight(fontSize).toPx().toInt() }
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    // Styled like a material Text, the code of an emote that failed to load stands in for it
    val textStyle = LocalTextStyle.current
    val fallbackStyle = textStyle.merge(color = textStyle.color.takeOrElse { LocalContentColor.current }, fontSize = fontSize.sp)
    val inlineImages = remember(badges, emotes, baseHeightPx, emoteCoordinator) { InlineImages(badges, emotes, baseHeightPx, emoteCoordinator) }
    LaunchedEffect(inlineImages, isPageVisible) {
        if (isPageVisible) {
            inlineImages.loadMissing(context) { code -> textMeasurer.measure(AnnotatedString(code), fallbackStyle) }
        }
    }
    // Offscreen pages skip registration, so their animation frames never invalidate a row
    DisposableEffect(inlineImages, isPageVisible) {
        inlineImages.setInvalidationsEnabled(isPageVisible)
        onDispose { inlineImages.setInvalidationsEnabled(false) }
    }
    return inlineImages
}

/** Badges and emotes drawn into the placeholders of a message text, without a composable per image. */
@Stable
internal class InlineImages(
    badges: List<BadgeUi>,
    emotes: List<EmoteUi>,
    private val baseHeightPx: Int,
    private val emoteCoordinator: EmoteAnimationCoordinator,
) {
    private val badges = badges.associateBy(::badgeInlineId)
    private val emotes = emotes.associateBy(::emoteInlineId)
    private val contents = HashMap<String, InlineImageContent>()
    private val registeredDrawables = HashSet<Drawable>()
    private var invalidationsEnabled = false

    // Read while drawing, so loads and animation frames only redraw the text
    private var drawTick by mutableIntStateOf(0)
    private val invalidationListener: () -> Unit = { drawTick++ }

    init {
        // Cache hits resolve synchronously, so the first frame already draws them
        this.badges.forEach { (id, badge) ->
            emoteCoordinator.getCachedBadge(badge, baseHeightPx)?.let { contents[id] = InlineImageContent.Image(it, badge.shape) }
        }
        this.emotes.forEach { (id, emote) ->
            emoteCoordinator.getCachedEmote(emote, baseHeightPx)?.let { contents[id] = InlineImageContent.Image(it, clipShape = null) }
        }
    }

    fun emoteFor(id: String): EmoteUi? = emotes[id]

    suspend fun loadMissing(
        context: PlatformContext,
        measureFallback: (code: String) -> TextLayoutResult,
    ) = coroutineScope {
        badges.forEach { (id, badge) ->
            if (id !in contents) {
                launch {
                    // Failed badges stay empty
                    val drawable = emoteCoordinator.loadBadge(context, badge, baseHeightPx) ?: return@launch
                    onLoaded(id, InlineImageContent.Image(drawable, badge.shape))
                }
            }
        }
        emotes.forEach { (id, emote) ->
            if (id !in contents) {
                launch {
                    val content =
                        when (val drawable = emoteCoordinator.loadEmote(context, emote, baseHeightPx)) {
                            null -> {
                                val fallback = measureFallback(emote.fallbackCode)
                                // Waiting rows resize the placeholder to the fallback text
                                emoteCoordinator.putDimensions(emote.cacheKey(baseHeightPx), fallback.size.width to fallback.size.height)
                                InlineImageContent.Fallback(fallback)
                            }

                            else -> {
                                InlineImageContent.Image(drawable, clipShape = null)
                            }
                        }
                    onLoaded(id, content)
                }
            }
        }
    }

    fun setInvalidationsEnabled(enabled: Boolean) {
        invalidationsEnabled = enabled
        updateRegistrations()
    }

    fun DrawScope.drawImages(
        layout: TextLayoutResult,
        placeholderIds: List<String>,
    ) {
        drawTick
        layout.placeholderRects.forEachIndexed { index, rect ->
            val content = rect?.let { contents[placeholderIds[index]] } ?: return@forEachIndexed
            when (content) {
                is InlineImageContent.Image -> drawImage(content, rect)
                is InlineImageContent.Fallback -> drawText(content.layout, topLeft = rect.topLeft)
            }
        }
    }

    private fun onLoaded(
        id: String,
        content: InlineImageContent,
    ) {
        contents[id] = content
        updateRegistrations()
        drawTick++
    }

    // The same drawable can fill several placeholders, it is registered once
    private fun updateRegistrations() {
        val drawables =
            when {
                invalidationsEnabled -> contents.values.mapNotNull { (it as? InlineImageContent.Image)?.drawable }.toSet()
                else -> emptySet()
            }
        (registeredDrawables - drawables).forEach { emoteCoordinator.unregisterInvalidationListener(it, invalidationListener) }
        (drawables - registeredDrawables).forEach { emoteCoordinator.registerInvalidationListener(it, invalidationListener) }
        registeredDrawables.clear()
        registeredDrawables.addAll(drawables)
    }

    private fun DrawScope.drawImage(
        content: InlineImageContent.Image,
        rect: Rect,
    ) {
        val bounds = content.drawable.bounds
        if (bounds.isEmpty) {
            return
        }
        // Fitted and centered, the placeholder only differs from the drawable while its size is estimated
        val scale = min(rect.width / bounds.width(), rect.height / bounds.height())
        val left = rect.left + (rect.width - bounds.width() * scale) / 2
        val top = rect.top + (rect.height - bounds.height() * scale) / 2
        val draw = {
            withTransform({
                translate(left, top)
                scale(scale, scale, pivot = Offset.Zero)
                translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            }) {
                drawIntoCanvas { content.drawable.draw(it.nativeCanvas) }
            }
        }
        when (val shape = content.clipShape) {
            null -> {
                draw()
            }

            else -> {
                val clip = Path().apply { addOutline(shape.createOutline(rect.size, layoutDirection, this@drawImage)) }
                clip.translate(rect.topLeft)
                clipPath(clip) { draw() }
            }
        }
    }
}

private sealed interface InlineImageContent {
    class Image(
        val drawable: Drawable,
        val clipShape: Shape?,
    ) : InlineImageContent

    class Fallback(
        val layout: TextLayoutResult,
    ) : InlineImageContent
}
