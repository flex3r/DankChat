package com.flxrs.dankchat.utils.extensions

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import kotlin.math.sin

@ColorInt
fun Int.normalizeColor(@ColorInt background: Int): Int {

    val isLightBackground = MaterialColors.isColorLight(background)
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this, hsl)
    val huePercentage = hsl[0] / 360f

    return when {
        isLightBackground -> {
            if (hsl[2] > 0.5f) {
                hsl[2] = 0.5f
            }

            if (hsl[2] > 0.4f && huePercentage > 0.1f && huePercentage < 0.33333f) {
                hsl[2] = (hsl[2] -
                        sin((huePercentage - 0.1f) / (0.33333f - 0.1f) * 3.14159f) * hsl[1] * 0.4f)
            }

            ColorUtils.HSLToColor(hsl)
        }

        else              -> {
            if (hsl[2] < 0.5f) {
                hsl[2] = 0.5f
            }

            if (hsl[2] < 0.6f && huePercentage > 0.54444f && huePercentage < 0.83333f) {
                hsl[2] = (hsl[2] +
                        sin((huePercentage - 0.54444f) / (0.83333f - 0.54444f) * 3.14159f) * hsl[1] * 0.4f)
            }

            ColorUtils.HSLToColor(hsl)
        }
    }
}

@ColorInt
fun Int.harmonize(context: Context): Int = MaterialColors.harmonizeWithPrimary(context, this)