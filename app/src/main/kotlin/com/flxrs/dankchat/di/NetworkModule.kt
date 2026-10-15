package com.flxrs.dankchat.di

import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.auth.AuthApi
import com.flxrs.dankchat.data.api.badges.BadgesApi
import com.flxrs.dankchat.data.api.bttv.BTTVApi
import com.flxrs.dankchat.data.api.dankchat.DankChatApi
import com.flxrs.dankchat.data.api.ffz.FFZApi
import com.flxrs.dankchat.data.api.helix.HelixApi
import com.flxrs.dankchat.data.api.helix.HelixApiStats
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApi
import com.flxrs.dankchat.data.api.seventv.SevenTVApi
import com.flxrs.dankchat.data.api.supibot.SupibotApi
import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

const val UPLOAD_OKHTTP_CLIENT = "UploadOkHttpClient"

private val okhttpLogger = KotlinLogging.logger("OkHttp")

@Module
class NetworkModule {
    private companion object {
        const val SUPIBOT_BASE_URL = "https://supinic.com/api/"
        const val HELIX_BASE_URL = "https://api.twitch.tv/helix/"
        const val AUTH_BASE_URL = "https://id.twitch.tv/oauth2/"
        const val DANKCHAT_BASE_URL = "https://api.dank.chat/v1/"
        const val BADGES_BASE_URL = "https://badges.twitch.tv/v1/badges/"
        const val FFZ_BASE_URL = "https://api.frankerfacez.com/v1/"
        const val BTTV_BASE_URL = "https://api.betterttv.net/3/cached/"
        const val SEVENTV_BASE_URL = "https://7tv.io/v3/"

        const val DEFAULT_TIMEOUT_MS = 30_000L

        // For best-effort APIs whose data no feature depends on, they should fail fast
        const val BEST_EFFORT_TIMEOUT_MS = 10_000L
    }

    @Single
    @Named(UPLOAD_OKHTTP_CLIENT)
    fun provideUploadOkHttpClient(): OkHttpClient = OkHttpClient
        .Builder()
        .callTimeout(60.seconds.toJavaDuration())
        .build()

    @Single
    fun provideJson(): Json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Single
    fun provideKtorClient(json: Json): HttpClient {
        val httpLogger = KotlinLogging.logger("HttpClient")
        return HttpClient(OkHttp) {
            engine {
                config {
                    dispatcher(createSafeOkHttpDispatcher())
                }
            }
            install(Logging) {
                level = LogLevel.INFO
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            httpLogger.trace { message }
                        }
                    }
            }
            install(HttpCache)
            install(UserAgent) {
                agent = "dankchat/${BuildConfig.VERSION_NAME}"
            }
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = DEFAULT_TIMEOUT_MS
                requestTimeoutMillis = DEFAULT_TIMEOUT_MS
                socketTimeoutMillis = DEFAULT_TIMEOUT_MS
            }
        }
    }

    @Single
    fun provideAuthApi(ktorClient: HttpClient) = AuthApi(
        ktorClient.config {
            defaultRequest {
                url(AUTH_BASE_URL)
            }
        },
    )

    @Single
    fun provideDankChatApi(ktorClient: HttpClient) = DankChatApi(
        ktorClient.config {
            defaultRequest {
                url(DANKCHAT_BASE_URL)
            }
        },
    )

    @Single
    fun provideSupibotApi(ktorClient: HttpClient) = SupibotApi(
        ktorClient.config {
            defaultRequest {
                url(SUPIBOT_BASE_URL)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = BEST_EFFORT_TIMEOUT_MS
                requestTimeoutMillis = BEST_EFFORT_TIMEOUT_MS
                socketTimeoutMillis = BEST_EFFORT_TIMEOUT_MS
            }
        },
    )

    @Single
    fun provideHelixApi(
        ktorClient: HttpClient,
        authDataStore: AuthDataStore,
        helixApiStats: HelixApiStats,
        startupValidationHolder: StartupValidationHolder,
    ) = HelixApi(
        ktorClient.config {
            defaultRequest {
                url(HELIX_BASE_URL)
                header("Client-ID", authDataStore.clientId)
            }
            install(ResponseObserver) {
                onResponse { response ->
                    helixApiStats.recordResponse(response.status.value)
                }
            }
        },
        authDataStore,
        startupValidationHolder,
    )

    @Single
    fun provideBadgesApi(ktorClient: HttpClient) = BadgesApi(
        ktorClient.config {
            defaultRequest {
                url(BADGES_BASE_URL)
            }
        },
    )

    @Single
    fun provideFFZApi(ktorClient: HttpClient) = FFZApi(
        ktorClient.config {
            defaultRequest {
                url(FFZ_BASE_URL)
            }
        },
    )

    @Single
    fun provideBTTVApi(ktorClient: HttpClient) = BTTVApi(
        ktorClient.config {
            defaultRequest {
                url(BTTV_BASE_URL)
            }
        },
    )

    @Single
    fun provideRecentMessagesApi(
        ktorClient: HttpClient,
        developerSettingsDataStore: DeveloperSettingsDataStore,
    ) = RecentMessagesApi(
        ktorClient.config {
            defaultRequest {
                url(developerSettingsDataStore.current().customRecentMessagesHost)
            }
        },
    )

    @Single
    fun provideSevenTVApi(ktorClient: HttpClient) = SevenTVApi(
        ktorClient.config {
            defaultRequest {
                url(SEVENTV_BASE_URL)
            }
        },
    )
}

private fun createSafeOkHttpDispatcher(): Dispatcher {
    val executor = ThreadPoolExecutor(
        0,
        Int.MAX_VALUE,
        60L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
    ) { runnable ->
        Thread(runnable, "OkHttp Dispatcher").apply {
            isDaemon = false
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, throwable ->
                okhttpLogger.error(throwable) { "Uncaught exception on OkHttp thread" }
            }
        }
    }
    return Dispatcher(executor)
}
