package com.flxrs.dankchat.utils.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.get
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import kotlin.math.sin

@ColorInt
fun Int.normalizeColor(isDarkMode: Boolean): Int {

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this, hsl)
    val huePercentage = hsl[0] / 360f
    if (isDarkMode) {
        if (hsl[2] < 0.5f) {
            hsl[2] = 0.5f
        }

        if (hsl[2] < 0.6f && huePercentage > 0.54444f && huePercentage < 0.83333f) {
            hsl[2] = (hsl[2] +
                    sin((huePercentage - 0.54444f) / (0.83333f - 0.54444f) * 3.14159f) * hsl[1] * 0.4f)
        }

        return ColorUtils.HSLToColor(hsl)
    }
    if (hsl[2] > 0.5f) {
        hsl[2] = 0.5f
    }

    if (hsl[2] > 0.4f && huePercentage > 0.1f && huePercentage < 0.33333f) {
        hsl[2] = (hsl[2] -
                sin((huePercentage - 0.1f) / (0.33333f - 0.1f) * 3.14159f) * hsl[1] * 0.4f)
    }
    return ColorUtils.HSLToColor(hsl)
}

@ColorInt
fun Int.harmonize(context: Context): Int = MaterialColors.harmonizeWithPrimary(context, this)