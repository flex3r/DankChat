package com.flxrs.dankchat.ui.chat.messages.common

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.ui.chat.EmoteUi
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class EmoteDimensions(
    val id: String,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * Text with badges, emotes and spacers in placeholders sized by [knownDimensions]. The images are drawn by [inlineImages]
 * instead of being composed, and taps on an emote go to [onEmoteClick].
 */
@Composable
internal fun TextWithMeasuredInlineContent(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    inlineImages: InlineImages? = null,
    knownDimensions: ImmutableMap<String, EmoteDimensions> = persistentMapOf(),
    onTextClick: ((Int) -> Unit)? = null,
    onBackgroundClick: (() -> Unit)? = null,
    onTextLongClick: ((Int) -> Unit)? = null,
    onEmoteClick: ((EmoteUi) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val density = LocalDensity.current
    val placeholders = remember(knownDimensions, text, density) { buildPlaceholders(knownDimensions, text, density) }
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val coroutineScope = rememberCoroutineScope()
    val measuredText = remember { mutableStateOf<MeasuredText?>(null) }
    val currentInlineImages = rememberUpdatedState(inlineImages)
    val currentOnTextClick = rememberUpdatedState(onTextClick)
    val currentOnBackgroundClick = rememberUpdatedState(onBackgroundClick)
    val currentOnTextLongClick = rememberUpdatedState(onTextLongClick)
    val currentOnEmoteClick = rememberUpdatedState(onEmoteClick)

    val measurePolicy =
        remember(text, style, placeholders, maxLines, overflow, textMeasurer) {
            MeasurePolicy { _, constraints ->
                val result =
                    textMeasurer.measure(
                        text = text,
                        style = style,
                        overflow = overflow,
                        maxLines = maxLines,
                        placeholders = placeholders.ranges,
                        constraints = constraints,
                        layoutDirection = layoutDirection,
                        density = this,
                    )
                measuredText.value = MeasuredText(result, placeholders.ids)
                layout(constraints.constrainWidth(result.size.width), constraints.constrainHeight(result.size.height)) {}
            }
        }

    Layout(
        measurePolicy = measurePolicy,
        modifier =
            modifier
                .semantics { this.text = text }
                .pointerInput(text, interactionSource) {
                    detectTapGestures(
                        onPress = { offset ->
                            // Emote presses open the emote, the message row shows no press for them
                            val isEmotePress = currentOnEmoteClick.value != null && measuredText.value?.emoteAt(offset, currentInlineImages.value) != null
                            if (interactionSource != null && !isEmotePress) {
                                coroutineScope.launch {
                                    emitPressInteractions(offset, interactionSource, coroutineScope)
                                }
                            }
                        },
                        onTap = { offset ->
                            val measured = measuredText.value ?: return@detectTapGestures
                            val layout = measured.layout
                            val emote = measured.emoteAt(offset, currentInlineImages.value)
                            val onEmote = currentOnEmoteClick.value
                            when {
                                emote != null && onEmote != null -> {
                                    onEmote(emote)
                                }

                                layout.isOnTextLine(offset) -> {
                                    currentOnTextClick.value?.invoke(layout.getOffsetForPosition(offset))
                                }

                                else -> {
                                    currentOnBackgroundClick.value?.invoke()
                                }
                            }
                        },
                        onLongPress = { offset ->
                            val layout = measuredText.value?.layout
                            val textOffset =
                                when {
                                    layout != null && layout.isOnTextLine(offset) -> layout.getOffsetForPosition(offset)
                                    else -> -1
                                }
                            currentOnTextLongClick.value?.invoke(textOffset)
                        },
                    )
                }.drawBehind {
                    val measured = measuredText.value ?: return@drawBehind
                    drawText(measured.layout)
                    inlineImages?.run { drawImages(measured.layout, measured.placeholderIds) }
                },
    )
}

// Placeholder ids are kept with the layout, their order matches its placeholder rects
private class MeasuredText(
    val layout: TextLayoutResult,
    val placeholderIds: List<String>,
)

private fun TextLayoutResult.isOnTextLine(offset: Offset): Boolean {
    val line = getLineForVerticalPosition(offset.y)
    return offset.x in getLineLeft(line)..getLineRight(line)
}

private fun MeasuredText.emoteAt(
    offset: Offset,
    inlineImages: InlineImages?,
): EmoteUi? {
    val index = layout.placeholderRects.indexOfFirst { it?.contains(offset) == true }
    return placeholderIds.getOrNull(index)?.let { inlineImages?.emoteFor(it) }
}

// Delayed like clickable presses in scrollable containers, so swipes and scrolls starting on the text show no ripple
private suspend fun PressGestureScope.emitPressInteractions(
    offset: Offset,
    source: MutableInteractionSource,
    scope: CoroutineScope,
) {
    var press: PressInteraction.Press? = null
    val delayedPress =
        scope.launch {
            delay(ViewConfiguration.getTapTimeout().toLong())
            press = PressInteraction.Press(offset).also { source.emit(it) }
        }
    val isReleased = tryAwaitRelease()
    val emittedPress = press
    when {
        delayedPress.isActive -> {
            delayedPress.cancelAndJoin()
            // A tap shorter than the delay still shows its press
            if (isReleased) {
                val tap = PressInteraction.Press(offset)
                source.emit(tap)
                source.emit(PressInteraction.Release(tap))
            }
        }

        emittedPress != null -> {
            source.emit(
                when {
                    isReleased -> PressInteraction.Release(emittedPress)
                    else -> PressInteraction.Cancel(emittedPress)
                },
            )
        }
    }
}

@Immutable
internal class TextPlaceholders(
    val ranges: List<AnnotatedString.Range<Placeholder>>,
    val ids: List<String>,
)

// Inline content without known dimensions keeps its alternate text, spacers are sized by their id
internal fun buildPlaceholders(
    knownDimensions: ImmutableMap<String, EmoteDimensions>,
    text: AnnotatedString,
    density: Density,
): TextPlaceholders {
    val ranges = mutableListOf<AnnotatedString.Range<Placeholder>>()
    val ids = mutableListOf<String>()
    text.getStringAnnotations(INLINE_CONTENT_TAG, 0, text.length).forEach { annotation ->
        val id = annotation.item
        val dimensions =
            knownDimensions[id] ?: when {
                isSpacerId(id) -> EmoteDimensions(id, with(density) { spacerWidthDp(id).dp.toPx().toInt() }, 1)
                else -> return@forEach
            }
        val placeholder =
            with(density) {
                Placeholder(
                    width = dimensions.widthPx.toDp().toSp(),
                    height = dimensions.heightPx.toDp().toSp(),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                )
            }
        ranges += AnnotatedString.Range(placeholder, annotation.start, annotation.end)
        ids += id
    }
    return TextPlaceholders(ranges, ids)
}

internal const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"
