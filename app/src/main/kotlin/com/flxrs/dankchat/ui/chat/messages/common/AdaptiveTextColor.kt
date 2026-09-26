package com.flxrs.dankchat.ui.chat.messages.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.flxrs.dankchat.ui.theme.LocalAdaptiveColors
import com.flxrs.dankchat.utils.extensions.normalizeColor
import com.materialkolor.ktx.isLight

/**
 * Resolves the effective opaque background for contrast calculations.
 * Semi-transparent colors are composited over the surface color.
 */
@Composable
@ReadOnlyComposable
private fun resolveEffectiveBackground(backgroundColor: Color): Color {
    val surfaceColor = MaterialTheme.colorScheme.surface
    return when {
        backgroundColor == Color.Transparent -> surfaceColor
        backgroundColor.alpha < 1f -> backgroundColor.compositeOver(surfaceColor)
        else -> backgroundColor
    }
}

/**
 * Returns appropriate text color (light or dark) based on background brightness.
 * Uses Color.isLight() to determine if background is light,
 * then selects dark text for light backgrounds and vice versa.
 *
 * For transparent backgrounds, uses the surface color for brightness calculation
 * since that's what will be visible behind the text.
 */
@Composable
fun rememberAdaptiveTextColor(backgroundColor: Color): Color {
    val adaptiveColors = LocalAdaptiveColors.current
    val effectiveBackground = resolveEffectiveBackground(backgroundColor)
    val isLightBackground = effectiveBackground.isLight()
    val baseColor = if (isLightBackground) adaptiveColors.onSurfaceLight else adaptiveColors.onSurfaceDark
    val effectiveBgArgb = effectiveBackground.toArgb()

    return remember(baseColor, effectiveBgArgb) {
        Color(baseColor.toArgb().normalizeColor(effectiveBgArgb))
    }
}

private const val MIN_LINK_CONTRAST_RATIO = 3.0

/**
 * Returns a link color with sufficient contrast against the given background.
 * Uses the theme's primary color when contrast is adequate, otherwise falls back
 * to a lighter/darker variant that meets the minimum contrast ratio.
 */
@Composable
fun rememberAdaptiveLinkColor(backgroundColor: Color): Color {
    val primary = MaterialTheme.colorScheme.primary
    val inversePrimary = MaterialTheme.colorScheme.inversePrimary
    val effectiveBg = resolveEffectiveBackground(backgroundColor)

    return remember(primary, inversePrimary, effectiveBg) {
        val primaryContrast = ColorUtils.calculateContrast(primary.toArgb(), effectiveBg.toArgb())
        when {
            primaryContrast >= MIN_LINK_CONTRAST_RATIO -> primary
            else -> inversePrimary
        }
    }
}

/**
 * Normalizes a raw color int for readable contrast against the effective background.
 * Semi-transparent backgrounds are composited over [MaterialTheme.colorScheme.surface]
 * to produce an opaque color for accurate contrast calculation.
 */
@Composable
fun rememberNormalizedColor(
    rawColor: Int,
    backgroundColor: Color,
): Color {
    val effectiveBg = resolveEffectiveBackground(backgroundColor)
    val effectiveBgArgb = effectiveBg.toArgb()

    return remember(rawColor, effectiveBgArgb) {
        Color(rawColor.normalizeColor(effectiveBgArgb))
    }
}
