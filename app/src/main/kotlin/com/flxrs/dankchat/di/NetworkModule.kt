package com.flxrs.dankchat.di

import android.util.Log
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.data.api.*
import com.flxrs.dankchat.data.api.auth.AuthApi
import com.flxrs.dankchat.data.api.auth.AuthApiClient
import com.flxrs.dankchat.data.api.badges.BadgesApi
import com.flxrs.dankchat.data.api.bttv.BTTVApi
import com.flxrs.dankchat.data.api.chatters.ChattersApi
import com.flxrs.dankchat.data.api.dankchat.DankChatApi
import com.flxrs.dankchat.data.api.ffz.FFZApi
import com.flxrs.dankchat.data.api.helix.HelixApi
import com.flxrs.dankchat.data.api.recentmessages.RecentMessagesApi
import com.flxrs.dankchat.data.api.seventv.SevenTVApi
import com.flxrs.dankchat.data.api.supibot.SupibotApi
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
        .callTimeout(20.seconds.toJavaDuration())
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
        .build()

    @Singleton
    @Provides
    fun provideJson(): Json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Singleton
    @Provides
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
    }

    @Singleton
    @Provides
    fun provideAuthApi(ktorClient: HttpClient) = AuthApi(ktorClient.config {
        defaultRequest {
            url(AUTH_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideDankChatApi(ktorClient: HttpClient) = DankChatApi(ktorClient.config {
        defaultRequest {
            url(DANKCHAT_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideSupibotApi(ktorClient: HttpClient) = SupibotApi(ktorClient.config {
        defaultRequest {
            url(SUPIBOT_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideHelixApi(ktorClient: HttpClient, preferenceStore: DankChatPreferenceStore) = HelixApi(ktorClient.config {
        defaultRequest {
            url(HELIX_BASE_URL)
            header("Client-ID", AuthApiClient.CLIENT_ID)
        }
    }, preferenceStore)

    @Singleton
    @Provides
    fun provideTmiApi(ktorClient: HttpClient) = ChattersApi(ktorClient.config {
        defaultRequest {
            url(TMI_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideBadgesApi(ktorClient: HttpClient) = BadgesApi(ktorClient.config {
        defaultRequest {
            url(BADGES_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideFFZApi(ktorClient: HttpClient) = FFZApi(ktorClient.config {
        defaultRequest {
            url(FFZ_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideBTTVApi(ktorClient: HttpClient) = BTTVApi(ktorClient.config {
        defaultRequest {
            url(BTTV_BASE_URL)
        }
    })

    @Singleton
    @Provides
    fun provideRecentMessagesApi(ktorClient: HttpClient, preferenceStore: DankChatPreferenceStore) = RecentMessagesApi(ktorClient.config {
        defaultRequest {
            url(preferenceStore.customRmHost)
        }
    })

    @Singleton
    @Provides
    fun provideSevenTVApi(ktorClient: HttpClient) = SevenTVApi(ktorClient.config {
        defaultRequest {
            url(SEVENTV_BASE_URL)
        }
    })
}