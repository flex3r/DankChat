package com.flxrs.dankchat

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.flxrs.dankchat.data.repo.HighlightsRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.di.ApplicationScope
import com.flxrs.dankchat.utils.tryClearEmptyFiles
import dagger.hilt.android.HiltAndroidApp
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DankChatApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    @Inject
    lateinit var highlightsRepository: HighlightsRepository

    @Inject
    lateinit var ignoresRepository: IgnoresRepository

    override fun onCreate() {
        super.onCreate()
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

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .build()
            }
            .components {
                val decoder = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> AnimatedImageDecoder.Factory()
                    else                                           -> GifDecoder.Factory() //GifDrawableDecoder.Factory()
                }
                add(decoder)
                val client = HttpClient(OkHttp) {
                    install(UserAgent) {
                        agent = "dankchat/${BuildConfig.VERSION_NAME}"
                    }
                }
                val fetcher = KtorNetworkFetcherFactory(
                    httpClient = { client },
                    cacheStrategy = { CacheControlCacheStrategy() },
                )
                add(fetcher)
            }
            .build()
    }
}
