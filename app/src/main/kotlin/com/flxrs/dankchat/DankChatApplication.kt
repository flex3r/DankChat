package com.flxrs.dankchat

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.flxrs.dankchat.data.repo.HighlightsRepository
import com.flxrs.dankchat.data.repo.IgnoresRepository
import com.flxrs.dankchat.di.DankChatModule
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.domain.BatterySaverCoordinator
import com.flxrs.dankchat.domain.ConnectionCoordinator
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
import kotlinx.coroutines.runBlocking
import okhttp3.Dispatcher
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

// Emote CDNs serve multi-megabyte animations next to tiny emotes, a low limit queues the small ones behind them
private const val IMAGE_MAX_REQUESTS_PER_HOST = 16

@KoinApplication(modules = [DankChatModule::class])
object DankChatKoinApp

class DankChatApplication :
    Application(),
    SingletonImageLoader.Factory {
    private val dispatchersProvider: DispatchersProvider by inject()
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchersProvider.main) }

    private val highlightsRepository: HighlightsRepository by inject()
    private val ignoresRepository: IgnoresRepository by inject()
    private val appearanceSettingsDataStore: AppearanceSettingsDataStore by inject()
    private val connectionCoordinator: ConnectionCoordinator by inject()
    private val batterySaverCoordinator: BatterySaverCoordinator by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin<DankChatKoinApp> {
            androidContext(this@DankChatApplication)
        }

        connectionCoordinator.initialize()
        batterySaverCoordinator.initialize()

        setupThemeMode()

        highlightsRepository.runMigrationsIfNeeded()
        ignoresRepository.runMigrationsIfNeeded()
        scope.launch(dispatchersProvider.io) {
            tryClearEmptyFiles(this@DankChatApplication)
        }
    }

    private fun setupThemeMode() {
        val theme = runBlocking { appearanceSettingsDataStore.settings.first().theme }
        val nightMode = when (theme) {
            Dark -> AppCompatDelegate.MODE_NIGHT_YES
            System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader
        .Builder(this)
        .diskCache {
            DiskCache
                .Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .build()
        }.components {
            // minSdk 30 guarantees AnimatedImageDecoder support (API 28+)
            add(AnimatedImageDecoder.Factory())
            val client =
                HttpClient(OkHttp) {
                    engine {
                        config {
                            dispatcher(Dispatcher().apply { maxRequestsPerHost = IMAGE_MAX_REQUESTS_PER_HOST })
                        }
                    }
                    install(UserAgent) {
                        agent = "dankchat/${BuildConfig.VERSION_NAME}"
                    }
                }
            val fetcher =
                KtorNetworkFetcherFactory(
                    httpClient = { client },
                    cacheStrategy = { CacheControlCacheStrategy() },
                )
            add(fetcher)
        }.build()
}
