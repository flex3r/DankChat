package com.flxrs.dankchat.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WebSocketOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmoteOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadOkHttpClient