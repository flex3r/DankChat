package com.flxrs.dankchat.di

import android.content.Context
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object PreferenceModule {

    @Singleton
    @Provides
    fun provideDankChatPreferenceStore(@ApplicationContext context: Context): DankChatPreferenceStore = DankChatPreferenceStore(context)
}