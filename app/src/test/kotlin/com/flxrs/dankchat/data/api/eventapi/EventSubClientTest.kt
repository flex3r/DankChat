package com.flxrs.dankchat.data.api.eventapi

import com.flxrs.dankchat.data.api.eventapi.dto.EventSubSubscriptionRequestDto
import com.flxrs.dankchat.data.api.eventapi.dto.EventSubSubscriptionResponseDto
import com.flxrs.dankchat.data.api.eventapi.dto.EventSubSubscriptionResponseListDto
import com.flxrs.dankchat.data.api.helix.HelixApiClient
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.utils.ForegroundServiceState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

internal class EventSubClientTest {
    private val server = MockWebServer()
    private val httpClient = HttpClient(OkHttp)
    private val dispatchers =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = Dispatchers.Default
            override val io: CoroutineDispatcher = Dispatchers.IO
            override val main: CoroutineDispatcher = Dispatchers.Default
            override val immediate: CoroutineDispatcher = Dispatchers.Default
        }
    private val json =
        Json {
            explicitNulls = false
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    private val topic = EventSubTopic.ChannelModerate("channel".toUserName(), "1".toUserId(), "2".toUserId())
    private val subscriptionRequests = CopyOnWriteArrayList<EventSubSubscriptionRequestDto>()
    private val sockets = CopyOnWriteArrayList<WebSocket>()
    private val closedSockets = CopyOnWriteArrayList<WebSocket>()
    private val sessionCounter = AtomicInteger()

    private val keepAliveSeconds = 10

    private val helixApiClient: HelixApiClient =
        mockk {
            coEvery { postEventSubSubscription(any()) } answers {
                val request = firstArg<EventSubSubscriptionRequestDto>()
                subscriptionRequests += request
                val subscription: EventSubSubscriptionResponseDto = mockk { every { id } returns "sub-${request.transport.sessionId}" }
                Result.success(EventSubSubscriptionResponseListDto(data = listOf(subscription), total = 1, totalCost = 0, maxTotalCost = 0))
            }
        }

    private val listener =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                sockets += webSocket
                webSocket.send(welcome("session-${sessionCounter.incrementAndGet()}"))
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                closedSockets += webSocket
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                closedSockets += webSocket
            }
        }

    private lateinit var client: EventSubClient

    @BeforeEach
    fun setup() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                    WS_PATH -> MockResponse.Builder().webSocketUpgrade(listener).build()
                    else -> MockResponse.Builder().code(503).build()
                }
            }
        server.start()
        client = EventSubClient(helixApiClient, json, ForegroundServiceState(), httpClient, dispatchers, defaultUrl = serverUrl(WS_PATH, scheme = "ws"))
    }

    @AfterEach
    fun cleanup() {
        sockets.forEach { runCatching { it.close(1000, null) } }
        server.close()
        httpClient.close()
    }

    @Test
    fun `server close reconnects with a fresh session and resubscribes`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                client.subscribe(topic)
                awaitSubscribed("session-1")

                sockets.last().close(4000, "internal server error")

                client.state.first { it == EventSubClientState.Connected("session-2") }
                awaitSubscribed("session-2")
            }
        }
    }

    @Test
    fun `migration keeps subscriptions and closes the old session`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                client.subscribe(topic)
                awaitSubscribed("session-1")
                val oldSocket = sockets.last()

                // The client upgrades ws:// reconnect urls to wss://, an uppercase scheme keeps the mock server reachable without TLS
                oldSocket.send(reconnect(serverUrl(WS_PATH, scheme = "WS")))

                client.state.first { it == EventSubClientState.Connected("session-2") }
                awaitCondition { oldSocket in closedSockets }
                assertEquals(1, subscriptionRequests.size)
                assertEquals(
                    setOf("sub-session-1"),
                    client.topics.value
                        .map { it.id }
                        .toSet(),
                )
            }
        }
    }

    @Test
    fun `failed migration falls back to a fresh session and resubscribes`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                client.subscribe(topic)
                awaitSubscribed("session-1")
                val oldSocket = sockets.last()

                oldSocket.send(reconnect(serverUrl("/broken", scheme = "WS")))

                client.state.first { it == EventSubClientState.Connected("session-2") }
                awaitSubscribed("session-2")
                assertTrue(oldSocket in closedSockets)
            }
        }
    }

    private suspend fun awaitSubscribed(sessionId: String) {
        client.topics.first { topics -> topics.any { it.id == "sub-$sessionId" } }
        assertTrue(subscriptionRequests.any { it.transport.sessionId == sessionId })
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        while (!condition()) {
            delay(50)
        }
    }

    private fun serverUrl(
        path: String,
        scheme: String,
    ): String = server.url(path).toString().replaceFirst("http", scheme)

    private fun welcome(sessionId: String): String = """{"metadata":{"message_id":"welcome-$sessionId","message_type":"session_welcome","message_timestamp":"$TIMESTAMP"},""" +
        """"payload":{"session":{"id":"$sessionId","status":"connected","connected_at":"$TIMESTAMP","keepalive_timeout_seconds":$keepAliveSeconds,"reconnect_url":null}}}"""

    private fun reconnect(url: String): String = """{"metadata":{"message_id":"reconnect-${System.nanoTime()}","message_type":"session_reconnect","message_timestamp":"$TIMESTAMP"},""" +
        """"payload":{"session":{"id":"old","status":"reconnecting","connected_at":"$TIMESTAMP","keepalive_timeout_seconds":null,"reconnect_url":"$url"}}}"""

    private companion object {
        const val WS_PATH = "/ws"
        const val TIMESTAMP = "2026-10-24T12:00:00.000000000Z"
    }
}
