package com.flxrs.dankchat.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class CoroutineModule {
    @Single
    fun provideDispatchersProvider(): DispatchersProvider = object : DispatchersProvider {
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val immediate: CoroutineDispatcher = Dispatchers.Main.immediate
    }
}

interface DispatchersProvider {
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val immediate: CoroutineDispatcher
}
