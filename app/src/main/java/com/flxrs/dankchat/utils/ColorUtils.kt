package com.flxrs.dankchat.utils

import androidx.core.graphics.ColorUtils

fun normalizeColor(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    val huePercentage = hsl[0] / 360f

    if (hsl[2] < 0.5f) {
        hsl[2] = 0.5f
    }

    if (hsl[2] < 0.6f && huePercentage > 0.54444f && huePercentage < 0.83333f) {
        hsl[2] = (hsl[2] + Math.sin((huePercentage - 0.54444) / (0.83333 - 0.54444) * 3.14159) * hsl[1] * 0.4).toFloat()
    }

    return ColorUtils.HSLToColor(hsl)
}