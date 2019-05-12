package com.flxrs.dankchat

import com.flxrs.dankchat.service.TwitchRepository
import com.flxrs.dankchat.service.irc.IrcConnection
import com.flxrs.dankchat.service.irc.IrcMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.koin.androidx.viewmodel.ext.koin.viewModel
import org.koin.dsl.module.module

val appModules = module {
	single { CoroutineScope(Dispatchers.IO + Job()) }
	single { TwitchRepository(get()) }
	single { (host: String, port: Int, timeout: Int, onError: () -> Unit, onMessage: (IrcMessage) -> Unit) -> IrcConnection(host, port, timeout, get(), onError, onMessage) }
	viewModel { DankChatViewModel(get()) }
}

