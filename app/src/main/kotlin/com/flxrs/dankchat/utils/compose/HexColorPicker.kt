package com.flxrs.dankchat.utils.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.R
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun HexColorPicker(
    color: Int,
    showAlpha: Boolean,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = rememberColorPickerController()
    val initialColor = remember { Color(color) }
    var lastFromPicker by remember { mutableStateOf<Int?>(null) }

    SideEffect(color) {
        if (color != lastFromPicker) {
            controller.selectByColor(Color(color), fromUser = false)
            lastFromPicker = color
        }
    }

    Column(modifier = modifier) {
        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .heightIn(max = 280.dp),
            controller = controller,
            initialColor = initialColor,
            onColorChanged = { envelope ->
                if (envelope.fromUser) {
                    val argb = envelope.color.toArgb()
                    lastFromPicker = argb
                    onColorChange(argb)
                }
            },
        )
        Spacer(Modifier.height(16.dp))
        val selectedColor by controller.selectedColor
        val brightnessPercent = (max(selectedColor.red, max(selectedColor.green, selectedColor.blue)) * 100).roundToInt()
        SliderLabelRow(
            label = stringResource(R.string.color_picker_brightness),
            percent = brightnessPercent,
        )
        Spacer(Modifier.height(4.dp))
        BrightnessSlider(
            modifier = Modifier
                .fillMaxWidth()
                .height(35.dp),
            controller = controller,
        )
        if (showAlpha) {
            Spacer(Modifier.height(12.dp))
            val opacityPercent = (selectedColor.alpha * 100).roundToInt()
            SliderLabelRow(
                label = stringResource(R.string.color_picker_opacity),
                percent = opacityPercent,
            )
            Spacer(Modifier.height(4.dp))
            AlphaSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp),
                controller = controller,
                tileOddColor = MaterialTheme.colorScheme.surface,
                tileEvenColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlphaTile(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                controller = controller,
                tileOddColor = MaterialTheme.colorScheme.surface,
                tileEvenColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            HexInputField(
                color = color,
                showAlpha = showAlpha,
                onColorChange = { newColor ->
                    lastFromPicker = newColor
                    controller.selectByColor(Color(newColor), fromUser = false)
                    onColorChange(newColor)
                },
                modifier = Modifier.width(180.dp),
            )
        }
    }
}

@Composable
private fun SliderLabelRow(
    label: String,
    percent: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = stringResource(R.string.color_picker_percent, percent),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun HexInputField(
    color: Int,
    showAlpha: Boolean,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxLength = if (showAlpha) 8 else 6
    var isFocused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(formatHex(color, showAlpha)) }

    SideEffect(color, showAlpha) {
        if (!isFocused) {
            text = formatHex(color, showAlpha)
        }
    }

    OutlinedTextField(
        modifier = modifier.onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            if (wasFocused && !focusState.isFocused) {
                text = formatHex(color, showAlpha)
            }
        },
        value = text,
        onValueChange = { input ->
            val filtered = input.filter { it.isHexDigit() }.take(maxLength)
            text = filtered
            if (filtered.length == maxLength) {
                parseHex(filtered, showAlpha)?.let(onColorChange)
            }
        },
        prefix = { Text("#") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun formatHex(
    color: Int,
    showAlpha: Boolean,
): String = when {
    showAlpha -> "%08X".format(Locale.ROOT, color)
    else -> "%06X".format(Locale.ROOT, color and 0xFFFFFF)
}

private fun parseHex(
    text: String,
    showAlpha: Boolean,
): Int? = runCatching {
    val long = text.toLong(16)
    when {
        showAlpha -> long.toInt()
        else -> (0xFF000000.toInt()) or long.toInt()
    }
}.getOrNull()
