package com.flxrs.dankchat.ui.chat.messages.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Replacement for deprecated [androidx.compose.foundation.text.ClickableText].
 * Renders annotated text with inline spacer support and URL click handling.
 */
@Composable
fun LinkableText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val density = LocalDensity.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val inlineContent = remember(text) {
        val spacerIds = text
            .getStringAnnotations(INLINE_CONTENT_TAG, 0, text.length)
            .filter { isSpacerId(it.item) }
            .map { it.item }
            .distinct()

        buildMap {
            for (id in spacerIds) {
                val widthSp = with(density) { spacerWidthDp(id).dp.toSp() }
                put(
                    id,
                    InlineTextContent(
                        Placeholder(width = widthSp, height = 0.01.sp, PlaceholderVerticalAlign.TextCenter),
                    ) { },
                )
            }
        }
    }

    BasicText(
        text = text,
        style = style,
        inlineContent = inlineContent,
        onTextLayout = { layoutResult.value = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures { offset ->
                layoutResult.value?.let { result ->
                    val position = result.getOffsetForPosition(offset)
                    val url = text.getStringAnnotations(URL_ANNOTATION_TAG, position, position).firstOrNull()
                    if (url != null) {
                        uriHandler.openUri(url.item)
                    }
                }
            }
        },
    )
}
