package com.flxrs.dankchat.ui.chat.messages.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch

data class EmoteDimensions(
    val id: String,
    val widthPx: Int,
    val heightPx: Int,
)

@Composable
fun TextWithMeasuredInlineContent(
    text: AnnotatedString,
    inlineContentProviders: ImmutableMap<String, @Composable () -> Unit>,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    knownDimensions: ImmutableMap<String, EmoteDimensions> = persistentMapOf(),
    onTextClick: ((Int) -> Unit)? = null,
    onBackgroundClick: (() -> Unit)? = null,
    onTextLongClick: ((Int) -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val density = LocalDensity.current
    val inlineContent = remember(knownDimensions, text, density, inlineContentProviders) {
        buildInlineContentMap(knownDimensions, text, density, inlineContentProviders)
    }
    ClickableInlineText(
        text = text,
        style = style,
        inlineContent = inlineContent,
        modifier = modifier,
        onTextClick = onTextClick,
        onBackgroundClick = onBackgroundClick,
        onTextLongClick = onTextLongClick,
        interactionSource = interactionSource,
    )
}

@Composable
private fun ClickableInlineText(
    text: AnnotatedString,
    style: TextStyle,
    inlineContent: Map<String, InlineTextContent>,
    onTextClick: ((Int) -> Unit)?,
    onBackgroundClick: (() -> Unit)?,
    onTextLongClick: ((Int) -> Unit)?,
    interactionSource: MutableInteractionSource?,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val textLayoutResultRef = remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentOnTextClick = rememberUpdatedState(onTextClick)
    val currentOnBackgroundClick = rememberUpdatedState(onBackgroundClick)
    val currentOnTextLongClick = rememberUpdatedState(onTextLongClick)

    BasicText(
        text = text,
        style = style,
        inlineContent = inlineContent,
        modifier =
            modifier.pointerInput(text, interactionSource) {
                detectTapGestures(
                    onPress = { offset ->
                        interactionSource?.let { source ->
                            val press = PressInteraction.Press(offset)
                            coroutineScope.launch {
                                source.emit(press)
                                tryAwaitRelease()
                                source.emit(PressInteraction.Release(press))
                            }
                        }
                    },
                    onTap = { offset ->
                        textLayoutResultRef.value?.let { layoutResult ->
                            val line = layoutResult.getLineForVerticalPosition(offset.y)
                            val lineLeft = layoutResult.getLineLeft(line)
                            val lineRight = layoutResult.getLineRight(line)
                            if (offset.x in lineLeft..lineRight) {
                                currentOnTextClick.value?.invoke(layoutResult.getOffsetForPosition(offset))
                            } else {
                                currentOnBackgroundClick.value?.invoke()
                            }
                        }
                    },
                    onLongPress = { offset ->
                        val layoutResult = textLayoutResultRef.value
                        if (layoutResult != null) {
                            val line = layoutResult.getLineForVerticalPosition(offset.y)
                            val lineLeft = layoutResult.getLineLeft(line)
                            val lineRight = layoutResult.getLineRight(line)
                            if (offset.x in lineLeft..lineRight) {
                                currentOnTextLongClick.value?.invoke(layoutResult.getOffsetForPosition(offset))
                            } else {
                                currentOnTextLongClick.value?.invoke(-1)
                            }
                        } else {
                            currentOnTextLongClick.value?.invoke(-1)
                        }
                    },
                )
            },
        onTextLayout = { layoutResult ->
            textLayoutResultRef.value = layoutResult
        },
    )
}

private fun buildInlineContentMap(
    knownDimensions: ImmutableMap<String, EmoteDimensions>,
    text: AnnotatedString,
    density: Density,
    providers: ImmutableMap<String, @Composable () -> Unit>,
): Map<String, InlineTextContent> {
    val allDimensions = buildMap {
        putAll(knownDimensions)
        // Resolve spacers from annotated string
        text
            .getStringAnnotations(INLINE_CONTENT_TAG, 0, text.length)
            .filter { isSpacerId(it.item) && it.item !in knownDimensions }
            .distinctBy { it.item }
            .forEach { annotation ->
                val widthPx = with(density) { spacerWidthDp(annotation.item).dp.toPx().toInt() }
                put(annotation.item, EmoteDimensions(annotation.item, widthPx, 1))
            }
    }
    return buildInlineContentMapFromDimensions(allDimensions, density, providers)
}

private fun buildInlineContentMapFromDimensions(
    dimensions: Map<String, EmoteDimensions>,
    density: Density,
    providers: ImmutableMap<String, @Composable () -> Unit>,
): Map<String, InlineTextContent> = dimensions.mapValues { (id, dims) ->
    InlineTextContent(
        placeholder =
            Placeholder(
                width = with(density) { dims.widthPx.toDp().toSp() },
                height = with(density) { dims.heightPx.toDp().toSp() },
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
    ) {
        providers[id]?.invoke()
    }
}

internal const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"
