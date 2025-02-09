package com.flxrs.dankchat.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [ConnectionModule::class, DatabaseModule::class, NetworkModule::class, CoroutineModule::class])
@ComponentScan("com.flxrs.dankchat")
class DankChatModule
