package com.flxrs.dankchat.preferences.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.theme.DankChatTheme

@Composable
fun PreferenceCategory(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            PreferenceCategoryTitle(title)
        }
        content()
    }
}

@Composable
fun PreferenceCategoryWithSummary(
    title: @Composable () -> Unit,
    summary: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
    ) {
        title()
        summary()
    }
}

@Composable
fun PreferenceCategoryTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
@PreviewLightDark
fun PreferenceCategoryPreview(@PreviewParameter(provider = LoremIpsum::class) loremIpsum: String) {
    DankChatTheme {
        Surface {
            PreferenceCategoryWithSummary(
                title = { PreferenceCategoryTitle("Title") },
                summary = { PreferenceSummary(loremIpsum.take(100)) }
            )
        }
    }
}

@Composable
@PreviewLightDark
fun PreferenceCategoryWithItemsPreview(@PreviewParameter(provider = LoremIpsum::class) loremIpsum: String) {
    DankChatTheme {
        Surface {
            PreferenceCategory(
                title = "Title",
                content = {
                    PreferenceItem("Appearence", Icons.Default.Palette)
                }
            )
        }
    }
}
