package com.flxrs.dankchat.utils.extensions

import android.content.res.Configuration
import android.content.res.Resources
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

fun WebSettings.setupDarkTheme(resources: Resources) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        val forceDark = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> WebSettingsCompat.FORCE_DARK_ON
            Configuration.UI_MODE_NIGHT_NO  -> WebSettingsCompat.FORCE_DARK_OFF
            else                            -> WebSettingsCompat.FORCE_DARK_AUTO
        }
        WebSettingsCompat.setForceDark(this, forceDark)
    }
}