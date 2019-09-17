package com.flxrs.dankchat.utils.extensions

import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.sin

@ColorInt
fun Int.normalizeColor(): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this, hsl)
    val huePercentage = hsl[0] / 360f

    if (hsl[2] < 0.5f) {
        hsl[2] = 0.5f
    }

    if (hsl[2] < 0.6f && huePercentage > 0.54444f && huePercentage < 0.83333f) {
        hsl[2] = (hsl[2] +
                sin((huePercentage - 0.54444) / (0.83333 - 0.54444) * 3.14159) * hsl[1] * 0.4).toFloat()
    }

    return ColorUtils.HSLToColor(hsl)
}