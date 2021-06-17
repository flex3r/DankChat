package com.flxrs.dankchat

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.flxrs.dankchat.di.EmoteOkHttpClient
import com.flxrs.dankchat.utils.GifDrawableDecoder
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class DankChatApplication : Application(), ImageLoaderFactory {
    @Inject
    @EmoteOkHttpClient
    lateinit var client: OkHttpClient

    override fun onCreate() {
        super.onCreate()

        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val nightMode = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.preference_dark_theme_key), true)
            .let { darkMode ->
                when {
                    // Force dark theme on < Android 8.1 because of statusbar/navigationbar issues
                    darkMode || (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 && !isTv) -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_NO
                }
            }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { client }
            .componentRegistry { add(GifDrawableDecoder()) }
            //.componentRegistry {
            //    if (SDK_INT >= 28) {
            //        add(ImageDecoderDecoder(this@DankChatApplication))
            //    } else {
            //        add(GifDecoder())
            //    }
            //}
            .build()
    }

    companion object {
        private val TAG = DankChatApplication::class.java.simpleName
    }
}