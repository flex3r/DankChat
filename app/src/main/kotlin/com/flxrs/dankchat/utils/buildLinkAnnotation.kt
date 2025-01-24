package com.flxrs.dankchat.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration

@Composable
fun textLinkStyles(): TextLinkStyles {
    return TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
        pressedStyle = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            background = MaterialTheme.colorScheme.primary.copy(alpha = ContentAlpha.medium),
        ),
    )
}

@Composable
fun buildLinkAnnotation(url: String): LinkAnnotation = LinkAnnotation.Url(
    url = url,
    styles = textLinkStyles(),
)
