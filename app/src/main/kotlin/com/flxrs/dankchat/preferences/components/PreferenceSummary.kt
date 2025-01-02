package com.flxrs.dankchat.preferences.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import com.flxrs.dankchat.utils.ContentAlpha

@Composable
fun PreferenceSummary(summary: AnnotatedString, modifier: Modifier = Modifier, isEnabled: Boolean = true) {
    val contentColor = LocalContentColor.current
    val color = when {
        isEnabled -> contentColor.copy(alpha = ContentAlpha.high)
        else      -> contentColor.copy(alpha = ContentAlpha.disabled)
    }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun PreferenceSummary(summary: String, isEnabled: Boolean = true) {
    val contentColor = LocalContentColor.current
    val color = when {
        isEnabled -> contentColor.copy(alpha = ContentAlpha.high)
        else      -> contentColor.copy(alpha = ContentAlpha.disabled)
    }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}
