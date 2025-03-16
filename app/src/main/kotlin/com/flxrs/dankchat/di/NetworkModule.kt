package com.flxrs.dankchat.di

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.auth.AuthApi
import com.flxrs.dankchat.data.api.badges.BadgesApi
import com.flxrs.dankchat.data.api.bttv.BTTVApi
import com.flxrs.dankchat.data.api.dankchat.DankChatApi
import com.flxrs.dankchat.data.api.ffz.FFZApi
import com.flxrs.dankchat.data.api.helix.HelixApi
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApi
import com.flxrs.dankchat.data.api.seventv.SevenTVApi
import com.flxrs.dankchat.data.api.supibot.SupibotApi
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
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
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

data object WebSocketOkHttpClient
data object UploadOkHttpClient

@Module
class NetworkModule {
    private companion object {
        const val SUPIBOT_BASE_URL = "https://supinic.com/api/"
        const val HELIX_BASE_URL = "https://api.twitch.tv/helix/"
        const val AUTH_BASE_URL = "https://id.twitch.tv/oauth2/"
        const val DANKCHAT_BASE_URL = "https://flxrs.com/api/"
        const val BADGES_BASE_URL = "https://badges.twitch.tv/v1/badges/"
        const val FFZ_BASE_URL = "https://api.frankerfacez.com/v1/"
        const val BTTV_BASE_URL = "https://api.betterttv.net/3/cached/"
        const val SEVENTV_BASE_URL = "https://7tv.io/v3/"
    }

    @Single
    @Named(type = WebSocketOkHttpClient::class)
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20.seconds.toJavaDuration())
        .build()

    @Single
    @Named(type = UploadOkHttpClient::class)
    fun provideUploadOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
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
    fun provideKtorClient(json: Json): HttpClient = HttpClient(OkHttp) {
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    Log.v("HttpClient", message)
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
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

    @Single
    fun provideAuthApi(ktorClient: HttpClient) = AuthApi(ktorClient.config {
        defaultRequest {
            url(AUTH_BASE_URL)
        }
    })

    @Single
    fun provideDankChatApi(ktorClient: HttpClient) = DankChatApi(ktorClient.config {
        defaultRequest {
            url(DANKCHAT_BASE_URL)
        }
    })

    @Single
    fun provideSupibotApi(ktorClient: HttpClient) = SupibotApi(ktorClient.config {
        defaultRequest {
            url(SUPIBOT_BASE_URL)
        }
    })

    @Single
    fun provideHelixApi(ktorClient: HttpClient, preferenceStore: DankChatPreferenceStore) = HelixApi(ktorClient.config {
        defaultRequest {
            url(HELIX_BASE_URL)
            header("Client-ID", preferenceStore.clientId)
        }
    }, preferenceStore)

    @Single
    fun provideBadgesApi(ktorClient: HttpClient) = BadgesApi(ktorClient.config {
        defaultRequest {
            url(BADGES_BASE_URL)
        }
    })

    @Single
    fun provideFFZApi(ktorClient: HttpClient) = FFZApi(ktorClient.config {
        defaultRequest {
            url(FFZ_BASE_URL)
        }
    })

    @Single
    fun provideBTTVApi(ktorClient: HttpClient) = BTTVApi(ktorClient.config {
        defaultRequest {
            url(BTTV_BASE_URL)
        }
    })

    @Single
    fun provideRecentMessagesApi(ktorClient: HttpClient, developerSettingsDataStore: DeveloperSettingsDataStore) = RecentMessagesApi(ktorClient.config {
        defaultRequest {
            url(developerSettingsDataStore.current().customRecentMessagesHost)
        }
    })

    @Single
    fun provideSevenTVApi(ktorClient: HttpClient) = SevenTVApi(ktorClient.config {
        defaultRequest {
            url(SEVENTV_BASE_URL)
        }
    })
}
