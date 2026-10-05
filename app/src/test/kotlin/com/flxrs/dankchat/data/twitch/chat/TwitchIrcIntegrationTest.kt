package com.flxrs.dankchat.data.twitch.chat

import com.flxrs.dankchat.data.auth.AuthDataStore
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.di.DispatchersProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class ChatConnectionTest {
    private val httpClient = HttpClient(OkHttp)
    private val mockServer = MockIrcServer()
    private val dispatchers =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = Dispatchers.Default
            override val io: CoroutineDispatcher = Dispatchers.IO
            override val main: CoroutineDispatcher = Dispatchers.Default
            override val immediate: CoroutineDispatcher = Dispatchers.Default
        }

    private lateinit var connection: ChatConnection

    @BeforeEach
    fun setup() {
        mockServer.start()
    }

    @AfterEach
    fun cleanup() {
        if (::connection.isInitialized) {
            connection.close()
        }
        mockServer.close()
        httpClient.close()
    }

    private fun createConnection(
        userName: String? = null,
        oAuth: String? = null,
        pongTimeout: Duration = 10.seconds,
    ): ChatConnection {
        val authDataStore: AuthDataStore =
            mockk {
                every { this@mockk.userName } returns userName?.toUserName()
                every { oAuthKey } returns oAuth
            }
        return ChatConnection(ChatConnectionType.Read, httpClient, authDataStore, dispatchers, serviceActive = MutableStateFlow(true), url = mockServer.url, pongTimeout = pongTimeout).also {
            connection = it
        }
    }

    @Test
    fun `anonymous connect sends correct CAP, PASS, NICK sequence`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                conn.connect()
                mockServer.frames.first { it == "NICK justinfan12781923" }

                assertEquals("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership", mockServer.sentFrames[0])
                assertEquals("PASS NaM", mockServer.sentFrames[1])
                assertEquals("NICK justinfan12781923", mockServer.sentFrames[2])
            }
        }
    }

    @Test
    fun `authenticated connect sends correct credentials`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection(userName = "testuser", oAuth = "oauth:abc123")
                conn.connect()
                mockServer.frames.first { it == "NICK testuser" }

                assertEquals("PASS oauth:abc123", mockServer.sentFrames[1])
                assertEquals("NICK testuser", mockServer.sentFrames[2])
            }
        }
    }

    @Test
    fun `connected state updates on successful handshake`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                assertFalse(conn.connected.value)

                conn.connect()
                conn.connected.first { it }
                assertTrue(conn.connected.value)
            }
        }
    }

    @Test
    fun `joinChannels sends JOIN command after connect`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                conn.joinChannels(listOf("testchannel".toUserName()))
                conn.connect()

                mockServer.frames.first { it.startsWith("JOIN") }
                assertContains(mockServer.sentFrames, "JOIN #testchannel")
            }
        }
    }

    @Test
    fun `partChannel sends PART command`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                val channel = "testchannel".toUserName()
                conn.joinChannels(listOf(channel))
                conn.connect()
                mockServer.frames.first { it.startsWith("JOIN") }

                conn.partChannel(channel)
                mockServer.frames.first { it.startsWith("PART") }
                assertContains(mockServer.sentFrames, "PART #testchannel")
            }
        }
    }

    @Test
    fun `sendMessage sends raw IRC through websocket`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                conn.connect()
                conn.connected.first { it }

                conn.sendMessage("PRIVMSG #test :hello world")
                mockServer.frames.first { it.startsWith("PRIVMSG") }
                assertContains(mockServer.sentFrames, "PRIVMSG #test :hello world")
            }
        }
    }

    @Test
    fun `close resets connected state`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                conn.connect()
                conn.connected.first { it }

                conn.close()
                assertFalse(conn.connected.value)
            }
        }
    }

    @Test
    fun `PING from server is answered with PONG`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                conn.connect()
                conn.connected.first { it }

                mockServer.sendToClient("PING :tmi.twitch.tv")
                mockServer.frames.first { it.startsWith("PONG") }
                assertContains(mockServer.sentFrames, "PONG :tmi.twitch.tv")
            }
        }
    }

    @Test
    fun `reconnectIfNecessary keeps connection when pong is received`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection(pongTimeout = 1.seconds)
                conn.connect()
                conn.connected.first { it }
                mockServer.frames.first { it.startsWith("CAP REQ") }

                conn.reconnectIfNecessary()
                mockServer.frames.first { it.startsWith("PING") }

                delay(2.seconds)
                assertTrue(conn.connected.value)
                assertEquals(1, mockServer.sentFrames.count { it.startsWith("CAP REQ") })
            }
        }
    }

    @Test
    fun `back to back reconnectIfNecessary calls keep a healthy connection`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection(pongTimeout = 1.seconds)
                conn.connect()
                conn.connected.first { it }
                mockServer.frames.first { it.startsWith("CAP REQ") }

                conn.reconnectIfNecessary()
                conn.reconnectIfNecessary()
                mockServer.frames.first { it.startsWith("PING") }

                delay(2.seconds)
                assertTrue(conn.connected.value)
                assertEquals(1, mockServer.sentFrames.count { it.startsWith("CAP REQ") })
            }
        }
    }

    @Test
    fun `reconnectIfNecessary reconnects when pong is not received`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                mockServer.respondToPing = false
                mockServer.enqueueUpgrade()

                val conn = createConnection(pongTimeout = 500.milliseconds)
                conn.joinChannels(listOf("ch1".toUserName()))
                conn.connect()
                // Any traffic proves the connection is alive, so the handshake has to be over
                conn.messages.first { it is ChatEvent.Connected }

                conn.reconnectIfNecessary()
                mockServer.frames.first { it.startsWith("PING") }

                mockServer.frames.first { mockServer.sentFrames.count { frame -> frame.startsWith("CAP REQ") } >= 2 }
                conn.connected.first { it }
            }
        }
    }

    @Test
    fun `multiple channels are joined via single JOIN command`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                conn.joinChannels(listOf("ch1".toUserName(), "ch2".toUserName()))
                conn.connect()

                mockServer.frames.first { it.contains("#ch1") && it.contains("#ch2") }
                val joinFrame = mockServer.sentFrames.first { it.startsWith("JOIN") }
                assertContains(joinFrame, "#ch1")
                assertContains(joinFrame, "#ch2")
            }
        }
    }
}
