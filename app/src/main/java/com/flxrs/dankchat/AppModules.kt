package com.flxrs.dankchat

import com.flxrs.dankchat.service.TwitchRepository
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.koin.androidx.viewmodel.ext.koin.viewModel
import org.koin.dsl.module.module

val appModules = module {
	factory { CoroutineScope(Dispatchers.IO + Job()) }
	single { (onMessage: (IrcMessage) -> Unit) -> WebSocketConnection(get(), onMessage) }
	single { TwitchRepository(get()) }
	viewModel { DankChatViewModel(get()) }
}

