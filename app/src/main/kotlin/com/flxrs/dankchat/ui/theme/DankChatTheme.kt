package com.flxrs.dankchat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.appearance.PaletteStylePreference
import com.flxrs.dankchat.preferences.appearance.ThemePreference
import com.flxrs.dankchat.preferences.appearance.ThemeSettings
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import org.koin.compose.koinInject

data class AdaptiveColors(
    val onSurfaceLight: Color,
    val onSurfaceDark: Color,
)

val LocalAdaptiveColors =
    staticCompositionLocalOf {
        AdaptiveColors(
            onSurfaceLight = lightColorScheme().onSurface,
            onSurfaceDark = darkColorScheme().onSurface,
        )
    }

@Composable
fun DankChatTheme(content: @Composable () -> Unit) {
    val inspectionMode = LocalInspectionMode.current
    val appearanceSettings = when {
        inspectionMode -> null
        else -> koinInject<AppearanceSettingsDataStore>()
    }
    // Other appearance settings change often and do not affect the colors
    val settings by appearanceSettings?.themeSettings?.collectAsStateWithLifecycle(
        initialValue = remember { appearanceSettings.current().themeSettings },
    ) ?: remember { mutableStateOf(ThemeSettings()) }

    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (settings.theme) {
        ThemePreference.System -> systemDarkTheme
        ThemePreference.Dark -> true
        ThemePreference.Light -> false
    }
    val accentColor = settings.accentColor
    val trueDarkTheme = settings.trueDarkTheme && darkTheme
    val paletteStyle = settings.paletteStyle.toPaletteStyle()
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val useSystemColors = accentColor == null && settings.paletteStyle == PaletteStylePreference.SystemDefault
    val seedColor = accentColor?.seedColor ?: when {
        dynamicColor -> dynamicLightColorScheme(LocalContext.current).primary
        else -> null
    }

    val lightColorScheme = when {
        seedColor != null && !useSystemColors -> rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = false,
            style = paletteStyle,
        )

        dynamicColor -> dynamicLightColorScheme(LocalContext.current)

        else -> expressiveLightColorScheme()
    }

    val darkColorScheme = when {
        seedColor != null && !useSystemColors -> rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = true,
            isAmoled = trueDarkTheme,
            style = paletteStyle,
        )

        dynamicColor && trueDarkTheme -> dynamicDarkColorScheme(LocalContext.current).copy(
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF222222),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF0E0E0E),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1C1C1C),
            background = Color.Black,
            onSurface = Color.White,
            onBackground = Color.White,
        )

        dynamicColor -> dynamicDarkColorScheme(LocalContext.current)

        else -> darkColorScheme()
    }

    val adaptiveColors =
        AdaptiveColors(
            onSurfaceLight = lightColorScheme.onSurface,
            onSurfaceDark = darkColorScheme.onSurface,
        )
    val colors = when {
        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
    ) {
        CompositionLocalProvider(LocalAdaptiveColors provides adaptiveColors) {
            content()
        }
    }
}

val ColorScheme.toolbarPillColor: Color
    get() = surfaceContainerHigh

private fun PaletteStylePreference.toPaletteStyle(): PaletteStyle = when (this) {
    PaletteStylePreference.SystemDefault -> PaletteStyle.TonalSpot
    PaletteStylePreference.TonalSpot -> PaletteStyle.TonalSpot
    PaletteStylePreference.Neutral -> PaletteStyle.Neutral
    PaletteStylePreference.Vibrant -> PaletteStyle.Vibrant
    PaletteStylePreference.Expressive -> PaletteStyle.Expressive
    PaletteStylePreference.Rainbow -> PaletteStyle.Rainbow
    PaletteStylePreference.FruitSalad -> PaletteStyle.FruitSalad
    PaletteStylePreference.Monochrome -> PaletteStyle.Monochrome
    PaletteStylePreference.Fidelity -> PaletteStyle.Fidelity
    PaletteStylePreference.Content -> PaletteStyle.Content
}
