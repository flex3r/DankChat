package com.flxrs.dankchat

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
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
import com.flxrs.dankchat.di.DankChatModule
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.appearance.AppearanceSettingsDataStore
import com.flxrs.dankchat.preferences.appearance.ThemePreference.Dark
import com.flxrs.dankchat.preferences.appearance.ThemePreference.System
import com.flxrs.dankchat.utils.tryClearEmptyFiles
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module

class DankChatApplication : Application(), SingletonImageLoader.Factory {

    private val dispatchersProvider: DispatchersProvider by inject()
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchersProvider.main) }

    private val highlightsRepository: HighlightsRepository by inject()
    private val ignoresRepository: IgnoresRepository by inject()
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DankChatApplication)
            modules(DankChatModule().module)
        }

        scope.launch {
            setupThemeMode()
        }

        highlightsRepository.runMigrationsIfNeeded()
        ignoresRepository.runMigrationsIfNeeded()
        scope.launch(dispatchersProvider.io) {
            tryClearEmptyFiles(this@DankChatApplication)
        }
    }

    private suspend fun setupThemeMode() {
        val uiModeManager = getSystemService<UiModeManager>()
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val theme = appearanceSettingsDataStore.settings.first().theme

        val supportsLightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 || isTv
        val supportsSystemDarkMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val nightMode = when {
            // Force dark theme on < Android 8.1 because of statusbar/navigationbar issues, or if system dark mode is not supported
            theme == Dark || !supportsLightMode || (theme == System && !supportsSystemDarkMode) -> AppCompatDelegate.MODE_NIGHT_YES
            theme == System                                                                     -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else                                                                                -> AppCompatDelegate.MODE_NIGHT_NO
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
