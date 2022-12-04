package com.flxrs.dankchat.utils.extensions

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.flxrs.dankchat.R
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
private fun Int.onlyRGB(): Int = this and 0xffffff

/** convert int to RGB with zero pad */
fun Int.toHexCode(): String = Integer.toHexString(this.onlyRGB()).padStart(6, '0')

/** convert RGB color (0xffffff) to ARGB with alpha (opaque by default) */
fun Int.withAlpha(alpha: Int = 255): Int = (alpha shl 24) or this.onlyRGB()


/** find a black/white (techincally colorOnSurface and colorOnSurfaceInverse) color that best contrast with `this` color
 * useful for example, when displaying text on this background color
 * */
fun Int.getContrastTextColor(context: Context? = null): Int {
    val color1 = context?.let {
        val typedValue = TypedValue()
        it.theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
        typedValue.data
    } ?: Color.BLACK

    val color2 = context?.let {
        val typedValue = TypedValue()
        it.theme.resolveAttribute(R.attr.colorOnSurfaceInverse, typedValue, true)
        typedValue.data
    } ?: Color.WHITE

    val possibleColors = listOf(color1, color2)
    val colorOpaque = this.withAlpha(255) // ensure opaque
    return possibleColors.maxBy { ColorUtils.calculateContrast(it, colorOpaque) }
}