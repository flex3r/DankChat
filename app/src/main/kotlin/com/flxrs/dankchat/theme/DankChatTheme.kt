package com.flxrs.dankchat.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.R

private val TrueDarkColorScheme = darkColorScheme(
    surface = Color.Black,
    background = Color.Black,
    onSurface = Color.White,
    onBackground = Color.White,
)

@Composable
fun DankChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    val trueDarkTheme = remember {
        when {
            inspectionMode -> false
            else           -> PreferenceManager
                .getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.preference_true_dark_theme_key), false)
        }

    }
    // Dynamic color is available on Android 12+
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamicColor && darkTheme && trueDarkTheme -> dynamicDarkColorScheme(LocalContext.current).copy(
            surface = TrueDarkColorScheme.surface,
            background = TrueDarkColorScheme.background,
        )

        dynamicColor && darkTheme                  -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor                               -> dynamicLightColorScheme(LocalContext.current)
        darkTheme && trueDarkTheme                 -> TrueDarkColorScheme
        darkTheme                                  -> darkColorScheme()
        else                                       -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
