package com.flxrs.dankchat.preferences.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.theme.DankChatTheme
import com.flxrs.dankchat.utils.ContentAlpha
import kotlin.math.roundToInt

@Composable
fun SwitchPreferenceItem(
    title: String,
    isChecked: Boolean,
    isEnabled: Boolean = true,
    onClick: (Boolean) -> Unit,
    summary: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    HorizontalPreferenceItemWrapper(
        title = title,
        icon = null,
        summary = summary,
        isEnabled = isEnabled,
        interactionSource = interactionSource,
        onClick = { onClick(!isChecked) },
        content = { Switch(checked = isChecked, enabled = isEnabled, onCheckedChange = onClick, interactionSource = interactionSource) },
    )
}

@Composable
fun SliderPreferenceItem(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onDrag: (Float) -> Unit,
    onDragFinished: () -> Unit,
    isEnabled: Boolean = true,
    summary: String? = null,
) {
    VerticalPreferenceItemWrapper(
        title = title,
        icon = null,
        summary = summary,
        isEnabled = isEnabled,
        content = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 16.dp),
            ) {
                Slider(
                    value = value,
                    onValueChange = onDrag,
                    onValueChangeFinished = onDragFinished,
                    valueRange = range,
                    steps = range.endInclusive.toInt() - range.start.toInt() - 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                )
                summary?.let {
                    Text(
                        text = "${value.roundToInt()}",
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    )
}

@Composable
fun PreferenceItem(
    title: String,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    summary: String? = null,
    onClick: () -> Unit = { },
) {
    HorizontalPreferenceItemWrapper(title, icon, summary, isEnabled, onClick)
}

@Composable
private fun HorizontalPreferenceItemWrapper(
    title: String,
    icon: ImageVector? = null,
    summary: String? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(48.dp)
            .clickable(
                enabled = isEnabled,
                onClick = onClick,
                interactionSource = interactionSource,
                indication = ripple(),
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreferenceItemContent(title, isEnabled, icon, summary)
            if (content != null) {
                Spacer(Modifier.width(8.dp))
                content()
            }
        }

    }
}

@Composable
private fun VerticalPreferenceItemWrapper(
    title: String,
    icon: ImageVector? = null,
    summary: String? = null,
    isEnabled: Boolean = true,
    onClick: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(48.dp)
            .clickable(
                enabled = isEnabled,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreferenceItemContent(title, isEnabled, icon, summary, textPaddingValues = PaddingValues())
        }
        content()
    }
}

@Composable
private fun RowScope.PreferenceItemContent(
    title: String,
    isEnabled: Boolean,
    icon: ImageVector?,
    summary: String?,
    textPaddingValues: PaddingValues = PaddingValues(vertical = 16.dp),
) {
    val contentColor = LocalContentColor.current
    val color = when {
        isEnabled -> contentColor
        else      -> contentColor.copy(alpha = ContentAlpha.disabled)
    }
    if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color,
        )
        Spacer(Modifier.width(32.dp))
    }
    Column(
        Modifier
            .padding(textPaddingValues)
            .weight(1f)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.basicMarquee(),
            maxLines = 1,
            color = color,
        )
        if (summary != null) {
            PreferenceSummary(summary, isEnabled)
        }
    }
}

@Composable
@PreviewLightDark
fun PreferenceItemPreview() {
    DankChatTheme {
        Surface {
            PreferenceItem("Appearance", Icons.Default.Palette, summary = "Summary")
        }
    }
}
