package com.flxrs.dankchat.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmoteOkHttpClient