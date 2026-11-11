package com.flxrs.dankchat.data.debug

import com.flxrs.dankchat.data.api.eventapi.EventSubClient
import com.flxrs.dankchat.data.api.eventapi.EventSubClientState
import com.flxrs.dankchat.data.api.seventv.eventapi.SevenTVEventApiClient
import com.flxrs.dankchat.data.twitch.chat.ChatConnection
import com.flxrs.dankchat.data.twitch.pubsub.PubSubManager
import com.flxrs.dankchat.di.READ_CONNECTION
import com.flxrs.dankchat.di.WRITE_CONNECTION
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Single
class ConnectionDebugSection(
    @Named(READ_CONNECTION) private val readConnection: ChatConnection,
    @Named(WRITE_CONNECTION) private val writeConnection: ChatConnection,
    private val eventSubClient: EventSubClient,
    private val pubSubManager: PubSubManager,
    private val sevenTVEventApiClient: SevenTVEventApiClient,
) : DebugSection {
    override val order = 3
    override val baseTitle = "Connection"

    override fun entries(): Flow<DebugSectionSnapshot> {
        val ticker =
            flow {
                while (true) {
                    emit(Unit)
                    delay(2_000)
                }
            }
        return combine(eventSubClient.state, eventSubClient.topics, readConnection.connected, writeConnection.connected, ticker) { state, topics, ircRead, ircWrite, _ ->
            val eventSubStatus =
                when (state) {
                    is EventSubClientState.Connected -> "Connected (${state.sessionId.take(8)}...)"
                    is EventSubClientState.Connecting -> "Connecting"
                    is EventSubClientState.Disconnected -> "Disconnected"
                }

            val pubSubStatus =
                when {
                    pubSubManager.connected -> "Connected"
                    else -> "Disconnected"
                }

            val sevenTvStatus = sevenTVEventApiClient.status()
            val sevenTvText =
                when {
                    sevenTvStatus.connected -> "Connected (${sevenTvStatus.subscriptionCount} subs)"
                    else -> "Disconnected"
                }

            val ircReadStatus = if (ircRead) "Connected" else "Disconnected"
            val ircWriteStatus = if (ircWrite) "Connected" else "Disconnected"

            DebugSectionSnapshot(
                title = baseTitle,
                entries =
                    listOf(
                        DebugEntry("IRC (read)", ircReadStatus),
                        DebugEntry("IRC (write)", ircWriteStatus),
                        DebugEntry("PubSub", pubSubStatus),
                        DebugEntry("EventSub", eventSubStatus),
                        DebugEntry("EventSub topics", "${topics.size}"),
                        DebugEntry("7TV EventAPI", sevenTvText),
                    ),
            )
        }
    }
}
