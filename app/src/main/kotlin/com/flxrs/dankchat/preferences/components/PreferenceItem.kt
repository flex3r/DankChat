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
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onClick: (Boolean) -> Unit,
    isEnabled: Boolean = true,
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

interface ExpandablePreferenceScope {
    fun dismiss()
}

@Composable
fun ExpandablePreferenceItem(
    title: String,
    icon: ImageVector? = null,
    isEnabled: Boolean = true,
    summary: String? = null,
    content: @Composable ExpandablePreferenceScope.() -> Unit,
) {
    var contentVisible by remember { mutableStateOf(false) }
    val scope = object : ExpandablePreferenceScope {
        override fun dismiss() {
            contentVisible = false
        }
    }
    val contentColor = LocalContentColor.current
    val color = when {
        isEnabled -> contentColor
        else      -> contentColor.copy(alpha = ContentAlpha.disabled)
    }
    HorizontalPreferenceItemWrapper(
        title = title,
        icon = icon,
        summary = summary,
        isEnabled = isEnabled,
        onClick = { contentVisible = true },
        content = { Icon(Icons.Default.KeyboardArrowDown, title, Modifier.padding(end = 4.dp), color) },
    )
    if (contentVisible) {
        content(scope)
    }
}

@Composable
fun SliderPreferenceItem(
    title: String,
    value: Float,
    onDrag: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    onDragFinished: () -> Unit,
    steps: Int = range.endInclusive.toInt() - range.start.toInt() - 1,
    isEnabled: Boolean = true,
    displayValue: Boolean = true,
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
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Slider(
                    value = value,
                    onValueChange = onDrag,
                    onValueChangeFinished = onDragFinished,
                    valueRange = range,
                    steps = steps,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                )
                if (displayValue) {
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
    trailingIcon: ImageVector? = null,
    isEnabled: Boolean = true,
    summary: String? = null,
    onClick: () -> Unit = { },
) {
    HorizontalPreferenceItemWrapper(
        title = title,
        icon = icon,
        summary = summary,
        isEnabled = isEnabled,
        onClick = onClick,
        content = trailingIcon?.let {
            {
                val contentColor = LocalContentColor.current
                val color = when {
                    isEnabled -> contentColor
                    else      -> contentColor.copy(alpha = ContentAlpha.disabled)
                }
                Icon(it, title, Modifier.padding(end = 4.dp), color)
            }
        }
    )
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
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit = {},
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
