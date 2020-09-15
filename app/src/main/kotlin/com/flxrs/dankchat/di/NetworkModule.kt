package com.flxrs.dankchat.di

import android.content.Context
import coil.util.CoilUtils
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.flxrs.dankchat.service.api.TwitchApi
import com.flxrs.dankchat.service.twitch.emote.EmoteManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CacheControl
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import javax.inject.Qualifier
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {
    @ApiOkHttpClient
    @Singleton
    @Provides
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(ChuckerInterceptor(context))
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
    fun provideTwitchApi(@ApiOkHttpClient client: OkHttpClient): TwitchApi = TwitchApi(client)

    @Singleton
    @Provides
    fun provideEmoteManager(twitchApi: TwitchApi): EmoteManager = EmoteManager(twitchApi)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiOkHttpClient
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmoteOkHttpClient