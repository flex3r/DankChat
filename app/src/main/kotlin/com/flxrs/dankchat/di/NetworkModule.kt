package com.flxrs.dankchat.di

import android.content.Context
import coil.util.CoilUtils
import com.flxrs.dankchat.BuildConfig
import com.flxrs.dankchat.service.api.*
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CacheControl
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {
    private const val KRAKEN_BASE_URL = "https://api.twitch.tv/kraken/"
    private const val SUPIBOT_BASE_URL = "https://supinic.com/api/"
    private const val HELIX_BASE_URL = "https://api.twitch.tv/helix/"
    private const val AUTH_BASE_URL = "https://id.twitch.tv/oauth2/"
    private const val DANKCHAT_BASE_URL = "https://flxrs.com/api/"
    private const val TMI_BASE_URL = "https://tmi.twitch.tv/"
    private const val BADGES_BASE_URL = "https://badges.twitch.tv/v1/badges/"
    private const val FFZ_BASE_URL = "https://api.frankerfacez.com/v1/"
    private const val BTTV_BASE_URL = "https://api.betterttv.net/3/cached/"
    private const val RECENT_MESSAGES_BASE_URL = "https://recent-messages.robotty.de/api/v2/"

    @ApiOkHttpClient
    @Singleton
    @Provides
    fun provideOkHttpClient(/*@ApplicationContext context: Context*/): OkHttpClient = OkHttpClient.Builder()
        //.addInterceptor(ChuckerInterceptor(context))
        .addInterceptor { chain ->
            chain.request().newBuilder()
                .header("User-Agent", "dankchat/${BuildConfig.VERSION_NAME}")
                .build()
                .let { chain.proceed(it) }
        }
        .build()

    @EmoteOkHttpClient
    @Singleton
    @Provides
    fun provideEmoteOkHttpClient(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(CoilUtils.createDefaultCache(context))
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 15 }) // increase from default 5
        //.addInterceptor(ChuckerInterceptor(context))
        .addInterceptor { chain ->
            val request = chain.request()
            try {
                chain.proceed(request)
            } catch (e: IllegalArgumentException) {
                val new = request.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
                chain.proceed(new)
            }
        }
        .build()

    @Singleton
    @Provides
    fun provideKrakenApiService(@ApiOkHttpClient client: OkHttpClient): KrakenApiService = Retrofit.Builder()
        .baseUrl(KRAKEN_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(KrakenApiService::class.java)

    @Singleton
    @Provides
    fun provideAuthApiService(@ApiOkHttpClient client: OkHttpClient): AuthApiService = Retrofit.Builder()
        .baseUrl(AUTH_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(AuthApiService::class.java)

    @Singleton
    @Provides
    fun provideDankChatApiService(@ApiOkHttpClient client: OkHttpClient): DankChatApiService = Retrofit.Builder()
        .baseUrl(DANKCHAT_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(DankChatApiService::class.java)

    @Singleton
    @Provides
    fun provideSupibotApiService(@ApiOkHttpClient client: OkHttpClient): SupibotApiService = Retrofit.Builder()
        .baseUrl(SUPIBOT_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(SupibotApiService::class.java)

    @Singleton
    @Provides
    fun provideHelixApiService(@ApiOkHttpClient client: OkHttpClient): HelixApiService = Retrofit.Builder()
        .baseUrl(HELIX_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(HelixApiService::class.java)

    @Singleton
    @Provides
    fun provideTmiApiService(@ApiOkHttpClient client: OkHttpClient): TmiApiService = Retrofit.Builder()
        .baseUrl(TMI_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(TmiApiService::class.java)

    @Singleton
    @Provides
    fun provideBadgesApiService(@ApiOkHttpClient client: OkHttpClient): BadgesApiService = Retrofit.Builder()
        .baseUrl(BADGES_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(BadgesApiService::class.java)

    @Singleton
    @Provides
    fun provideFFZApiService(@ApiOkHttpClient client: OkHttpClient): FFZApiService = Retrofit.Builder()
        .baseUrl(FFZ_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(FFZApiService::class.java)

    @Singleton
    @Provides
    fun provideBTTVApiService(@ApiOkHttpClient client: OkHttpClient): BTTVApiService = Retrofit.Builder()
        .baseUrl(BTTV_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(BTTVApiService::class.java)

    @Singleton
    @Provides
    fun provideRecentMessagesApiService(@ApiOkHttpClient client: OkHttpClient): RecentMessagesApiService = Retrofit.Builder()
        .baseUrl(RECENT_MESSAGES_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .client(client)
        .build()
        .create(RecentMessagesApiService::class.java)

    // @formatter:off
    @Singleton
    @Provides
    fun provideApiManager(@ApiOkHttpClient client: OkHttpClient, bttvApiService: BTTVApiService, dankChatApiService: DankChatApiService, ffzApiService: FFZApiService, helixApiService: HelixApiService, krakenApiService: KrakenApiService, recentMessagesApiService: RecentMessagesApiService, supibotApiService: SupibotApiService, authApiService: AuthApiService, badgesApiService: BadgesApiService, tmiApiService: TmiApiService): ApiManager = ApiManager(client, bttvApiService, dankChatApiService, ffzApiService, helixApiService, krakenApiService, recentMessagesApiService, supibotApiService, authApiService, badgesApiService, tmiApiService)
    // @formatter:on

    @Singleton
    @Provides
    fun provideEmoteManager(apiManager: ApiManager): EmoteManager = EmoteManager(apiManager)
}