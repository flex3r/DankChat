package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.api.eventapi.EventSubManager
import com.flxrs.dankchat.data.twitch.chat.ChatConnection
import com.flxrs.dankchat.data.twitch.chat.ConnectionState
import com.flxrs.dankchat.data.twitch.pubsub.PubSubManager
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.di.READ_CONNECTION
import com.flxrs.dankchat.di.WRITE_CONNECTION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class ChatConnector(
    @Named(READ_CONNECTION) private val readConnection: ChatConnection,
    @Named(WRITE_CONNECTION) private val writeConnection: ChatConnection,
    private val pubSubManager: PubSubManager,
    private val eventSubManager: EventSubManager,
    dispatchersProvider: DispatchersProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.default)
    private val connectionState = ConcurrentHashMap<UserName, MutableStateFlow<ConnectionState>>()

    val readEvents get() = readConnection.messages
    val writeEvents get() = writeConnection.messages
    val pubSubEvents get() = pubSubManager.messages
    val pubSubConnected get() = pubSubManager.connected
    val eventSubEvents get() = eventSubManager.events

    fun getConnectionState(channel: UserName): StateFlow<ConnectionState> = connectionState.getOrPut(channel) { MutableStateFlow(ConnectionState.DISCONNECTED) }

    fun setAllConnectionStates(state: ConnectionState) {
        connectionState.forEach { (_, flow) ->
            flow.value = state
        }
    }

    fun createConnectionState(channel: UserName) {
        connectionState.putIfAbsent(channel, MutableStateFlow(ConnectionState.DISCONNECTED))
    }

    fun removeConnectionState(channel: UserName) {
        connectionState.remove(channel)
    }

    fun connectAndJoin(channels: List<UserName>) {
        if (!readConnection.connected.value) {
            readConnection.connect()
            writeConnection.connect()

            if (channels.isNotEmpty()) {
                readConnection.joinChannels(channels)
            }
        }
    }

    fun closeAndReconnect(channels: List<UserName>) = scope.launch {
        readConnection.close()
        writeConnection.close()
        eventSubManager.close()
        connectAndJoin(channels)
    }

    fun reconnect(reconnectPubsub: Boolean = true) {
        readConnection.reconnect()
        writeConnection.reconnect()

        if (reconnectPubsub) {
            pubSubManager.reconnect()
            eventSubManager.reconnect()
        }
    }

    fun reconnectIfNecessary() {
        readConnection.reconnectIfNecessary()
        writeConnection.reconnectIfNecessary()
        pubSubManager.reconnectIfNecessary()
        eventSubManager.reconnectIfNecessary()
    }

    fun joinIrcChannel(channel: UserName) {
        readConnection.joinChannel(channel)
    }

    // IRC-only part that keeps all channel state, PubSub and EventSub topics intact,
    // used for temporarily leaving busy channels while the app is backgrounded
    fun partIrcChannel(channel: UserName) {
        scope.launch { readConnection.partChannel(channel) }
    }

    fun partChannel(channel: UserName) {
        scope.launch { readConnection.partChannel(channel) }
        pubSubManager.removeChannel(channel)
        eventSubManager.removeChannel(channel)
    }

    suspend fun sendRaw(message: String): Boolean = writeConnection.sendMessage(message)

    fun connectedAndHasModerateTopic(channel: UserName): Boolean = eventSubManager.connectedAndHasModerateTopic(channel)

    val connectedAndHasUserMessageTopic: Boolean get() = eventSubManager.connectedAndHasUserMessageTopic
}
