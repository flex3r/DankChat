package com.flxrs.dankchat.di

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.*
import com.flxrs.dankchat.data.repo.EmoteRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {
    private const val SUPIBOT_BASE_URL = "https://supinic.com/api/"
    private const val HELIX_BASE_URL = "https://api.twitch.tv/helix/"
    private const val AUTH_BASE_URL = "https://id.twitch.tv/oauth2/"
    private const val DANKCHAT_BASE_URL = "https://flxrs.com/api/"
    private const val TMI_BASE_URL = "https://tmi.twitch.tv/"
    private const val BADGES_BASE_URL = "https://badges.twitch.tv/v1/badges/"
    private const val FFZ_BASE_URL = "https://api.frankerfacez.com/v1/"
    private const val BTTV_BASE_URL = "https://api.betterttv.net/3/cached/"
    private const val SEVENTV_BASE_URL = "https://api.7tv.app/v2/"

    @WebSocketOkHttpClient
    @Singleton
    @Provides
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .build()

    @UploadOkHttpClient
    @Singleton
    @Provides
    fun provideUploadOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60.seconds.toJavaDuration())
        .build()

    @EmoteOkHttpClient
    @Singleton
    @Provides
    fun provideEmoteOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 20 }) // increase from default 5
        .build()

    @Singleton
    @Provides
    fun provideKtorClient(): HttpClient = HttpClient(OkHttp) {
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
            json(Json {
                explicitNulls = false
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    @Singleton
    @Provides
    fun provideAuthApiService(ktorClient: HttpClient) = AuthApiService(ktorClient.config {
        defaultRequest {
            url(AUTH_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideDankChatApiService(ktorClient: HttpClient) = DankChatApiService(ktorClient.config {
        defaultRequest {
            url(DANKCHAT_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideSupibotApiService(ktorClient: HttpClient) = SupibotApiService(ktorClient.config {
        defaultRequest {
            url(SUPIBOT_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideHelixApiService(ktorClient: HttpClient, preferenceStore: DankChatPreferenceStore) = HelixApiService(ktorClient.config {
        defaultRequest {
            url(HELIX_BASE_URL)
            header("Client-ID", ApiManager.CLIENT_ID)
        }
    }, preferenceStore)

    @Singleton
    @Provides
    fun provideTmiApiService(ktorClient: HttpClient) = TmiApiService(ktorClient.config {
        defaultRequest {
            url(TMI_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideBadgesApiService(ktorClient: HttpClient) = BadgesApiService(ktorClient.config {
        defaultRequest {
            url(BADGES_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideFFZApiService(ktorClient: HttpClient) = FFZApiService(ktorClient.config {
        defaultRequest {
            url(FFZ_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideBTTVApiService(ktorClient: HttpClient) = BTTVApiService(ktorClient.config {
        defaultRequest {
            url(BTTV_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideRecentMessagesApiService(ktorClient: HttpClient, preferenceStore: DankChatPreferenceStore) = RecentMessagesApiService(ktorClient.config {
        defaultRequest {
            url(preferenceStore.customRmHost)
        }
    })

    @Singleton
    @Provides
    fun provideSevenTVApiService(ktorClient: HttpClient) = SevenTVApiService(ktorClient.config {
        defaultRequest {
            url(SEVENTV_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideApiManager(
        @UploadOkHttpClient okHttpClient: OkHttpClient,
        bttvApiService: BTTVApiService,
        dankChatApiService: DankChatApiService,
        ffzApiService: FFZApiService,
        helixApiService: HelixApiService,
        recentMessagesApiService: RecentMessagesApiService,
        supibotApiService: SupibotApiService,
        authApiService: AuthApiService,
        badgesApiService: BadgesApiService,
        tmiApiService: TmiApiService,
        sevenTVApiService: SevenTVApiService,
        dankChatPreferenceStore: DankChatPreferenceStore
    ): ApiManager = ApiManager(
        okHttpClient,
        bttvApiService,
        dankChatApiService,
        ffzApiService,
        helixApiService,
        recentMessagesApiService,
        supibotApiService,
        authApiService,
        badgesApiService,
        tmiApiService,
        sevenTVApiService,
        dankChatPreferenceStore
    )

    @Singleton
    @Provides
    fun provideEmoteManager(apiManager: ApiManager, preferenceStore: DankChatPreferenceStore): EmoteRepository = EmoteRepository(apiManager, preferenceStore)
}