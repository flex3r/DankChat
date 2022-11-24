package com.flxrs.dankchat.utils.extensions

import android.content.Context
import android.graphics.Color
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


/** helper to extract only RGB part (i.e. drop the alpha part) */
fun Int.onlyRGB(): Int = this and 0xffffff

/** convert int to RGB with zero pad */
fun Int.toHexCode(): String = Integer.toHexString(this.onlyRGB()).padStart(6, '0')

/** convert RGB color (0xffffff) to ARGB with alpha */
fun Int.toARGBInt(alpha: Int = 255): Int = (alpha shl 24) or this.onlyRGB()


/** find best contrast text to display on specified background color (whtie or black) -- opacity ignored */
fun Int.getContrastTextColor(): Int {
    val possibleColors = listOf(Color.BLACK, Color.WHITE)
    val colorOpaque = this.toARGBInt()
    return possibleColors.maxBy { ColorUtils.calculateContrast(it, colorOpaque) }
}