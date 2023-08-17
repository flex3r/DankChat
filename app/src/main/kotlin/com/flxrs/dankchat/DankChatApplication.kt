package com.flxrs.dankchat

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.PredictiveBackControl
import androidx.preference.PreferenceManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import com.flxrs.dankchat.data.repo.HighlightsRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.di.EmoteOkHttpClient
import com.flxrs.dankchat.utils.tryClearEmptyFiles
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class DankChatApplication : Application(), ImageLoaderFactory {
    @Inject
    @EmoteOkHttpClient
    lateinit var emoteClient: OkHttpClient

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var highlightsRepository: HighlightsRepository

    @Inject
    lateinit var ignoresRepository: IgnoresRepository

    @OptIn(PredictiveBackControl::class)
    override fun onCreate() {
        super.onCreate()
        FragmentManager.enablePredictiveBack(false)
        setupThemeMode()

        highlightsRepository.runMigrationsIfNeeded()
        ignoresRepository.runMigrationsIfNeeded()
        scope.launch(Dispatchers.IO) {
            tryClearEmptyFiles(this@DankChatApplication)
        }
    }

    private fun setupThemeMode() {
        val uiModeManager = getSystemService<UiModeManager>()
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val followSystemKey = getString(R.string.preference_follow_system_key)
        val darkModeKey = getString(R.string.preference_dark_theme_key)
        val themeKey = getString(R.string.preference_theme_key)
        val theme = preferences.getString(themeKey, followSystemKey) ?: followSystemKey

        val supportsLightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 || isTv
        val supportsSystemDarkMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val nightMode = when {
            // Force dark theme on < Android 8.1 because of statusbar/navigationbar issues, or if system dark mode is not supported
            theme == darkModeKey || !supportsLightMode || (theme == followSystemKey && !supportsSystemDarkMode) -> AppCompatDelegate.MODE_NIGHT_YES
            theme == followSystemKey                                                                            -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else                                                                                                -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            //.logger(DebugLogger())
            .okHttpClient(emoteClient)
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .build()
            }
            .components {
                val decoder = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> ImageDecoderDecoder.Factory()
                    else                                           -> GifDecoder.Factory() //GifDrawableDecoder.Factory()
                }
                add(decoder)
            }
            .build()
    }
}
