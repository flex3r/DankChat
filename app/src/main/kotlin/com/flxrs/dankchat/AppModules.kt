package com.flxrs.dankchat

import com.flxrs.dankchat.chat.ChatViewModel
import com.flxrs.dankchat.service.ChatRepository
import com.flxrs.dankchat.service.DataRepository
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
    factory { (connectionName: String, client: OkHttpClient, request: Request, onDisconnect: (() -> Unit)?, onMessage: (IrcMessage) -> Unit) ->
        WebSocketConnection(
            connectionName = connectionName,
            scope = get(),
            client = client,
            request = request,
            onDisconnect = onDisconnect,
            onMessage = onMessage
        )
    }
    single { ChatRepository() }
    single { DataRepository() }
    viewModel { (channel: String) -> ChatViewModel(get(), channel) }
    viewModel { DankChatViewModel(get(), get()) }
}
