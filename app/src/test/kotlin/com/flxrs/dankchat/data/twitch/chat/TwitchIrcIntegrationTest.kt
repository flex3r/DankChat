package com.flxrs.dankchat.data.twitch.chat

import com.flxrs.dankchat.data.UserName
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
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
        activeChannel: UserName? = null,
    ): ChatConnection {
        val authDataStore: AuthDataStore =
            mockk {
                every { this@mockk.userName } returns userName?.toUserName()
                every { oAuthKey } returns oAuth
            }
        return ChatConnection(
            ChatConnectionType.Read,
            httpClient,
            authDataStore,
            dispatchers,
            serviceActive = MutableStateFlow(true),
            activeChannel = MutableStateFlow(activeChannel),
            url = mockServer.url,
            pongTimeout = pongTimeout,
        ).also {
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
    fun `reconnectIfNecessary while connecting keeps the pending connection`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                conn.joinChannels(listOf("ch1".toUserName()))
                conn.connect()
                conn.reconnectIfNecessary()

                val events = conn.messages.takeWhile { it !is ChatEvent.Connected }.toList()
                assertTrue(events.none { it is ChatEvent.Closed })
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
    fun `clean close by the server reconnects`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                mockServer.enqueueUpgrade()

                val conn = createConnection()
                conn.joinChannels(listOf("ch1".toUserName()))
                conn.connect()
                conn.messages.first { it is ChatEvent.Connected }

                mockServer.closeClientConnection()

                mockServer.frames.first { mockServer.sentFrames.count { frame -> frame.startsWith("CAP REQ") } >= 2 }
                conn.connected.first { it }
            }
        }
    }

    @Test
    fun `failed login does not reconnect`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                mockServer.enqueueUpgrade()

                val conn = createConnection(userName = "testuser", oAuth = "oauth:expired")
                conn.connect()
                mockServer.sendToClient(":tmi.twitch.tv NOTICE * :Login authentication failed")
                conn.messages.first { it is ChatEvent.Closed }

                delay(2.seconds)
                assertEquals(1, mockServer.sentFrames.count { it.startsWith("CAP REQ") })
                assertFalse(conn.connected.value)
            }
        }
    }

    @Test
    fun `identical consecutive messages are all delivered`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val conn = createConnection()
                conn.joinChannels(listOf("ch1".toUserName()))
                conn.connect()
                conn.messages.first { it is ChatEvent.Connected }

                // Rejection notices only carry a msg-id, so repeating the same action produces byte identical lines
                val notice = "@msg-id=msg_emoteonly :tmi.twitch.tv NOTICE #ch1 :This room is in emote-only mode."
                mockServer.sendToClient(notice)
                mockServer.sendToClient(notice)

                val notices =
                    conn.messages
                        .filterIsInstance<ChatEvent.Message>()
                        .filter { it.message.command == "NOTICE" }
                        .take(2)
                        .toList()
                assertEquals(2, notices.size)
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

    @Test
    fun `active channel is joined first`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val channels = (1..7).map { "ch$it".toUserName() }
                val conn = createConnection(activeChannel = "ch7".toUserName())
                conn.joinChannels(channels)
                conn.connect()

                val joinFrame = mockServer.frames.first { it.startsWith("JOIN") }
                assertTrue(joinFrame.startsWith("JOIN #ch7,"), joinFrame)
            }
        }
    }

    @Test
    fun `channel removed while its join is queued is not joined`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                val channels = (1..6).map { "ch$it".toUserName() }
                val conn = createConnection()
                conn.joinChannels(channels)
                conn.connect()

                val firstJoin = mockServer.frames.first { it.startsWith("JOIN") }
                val joined = firstJoin.removePrefix("JOIN ").split(",")
                val queued = channels.single { "#$it" !in joined }
                conn.partChannel(queued)

                // The second chunk follows the first one after three seconds
                delay(4.seconds)
                assertEquals(1, mockServer.sentFrames.count { it.startsWith("JOIN") })
            }
        }
    }

    @Test
    fun `pong pending from a closed session does not reconnect the next one`() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(15.seconds) {
                mockServer.respondToPing = false
                mockServer.enqueueUpgrade()
                mockServer.enqueueUpgrade()

                val conn = createConnection()
                conn.connect()
                mockServer.frames.first { it.startsWith("PING") }

                mockServer.closeClientConnection()
                mockServer.frames.first { mockServer.sentFrames.count { frame -> frame.startsWith("PING") } >= 2 }

                delay(2.seconds)
                assertEquals(2, mockServer.sentFrames.count { it.startsWith("CAP REQ") })
            }
        }
    }
}
