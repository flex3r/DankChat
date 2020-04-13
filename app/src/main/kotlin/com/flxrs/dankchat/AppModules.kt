package com.flxrs.dankchat

import com.flxrs.dankchat.service.TwitchRepository
import com.flxrs.dankchat.service.irc.IrcMessage
import com.flxrs.dankchat.service.twitch.connection.WebSocketConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModules = module {
    factory { CoroutineScope(Dispatchers.IO + Job()) }
    factory { (client: OkHttpClient, request: Request, onDisconnect: (() -> Unit)?, onMessage: (IrcMessage) -> Unit) ->
        WebSocketConnection(
            get(),
            client,
            request,
            onDisconnect,
            onMessage
        )
    }
    single { TwitchRepository(get()) }
    viewModel { DankChatViewModel(get()) }
}
