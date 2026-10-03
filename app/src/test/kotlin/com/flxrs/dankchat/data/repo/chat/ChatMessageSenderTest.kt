package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.repo.channel.ChannelRepository
import com.flxrs.dankchat.data.toUserId
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.data.twitch.message.RoomState
import com.flxrs.dankchat.data.twitch.message.RoomStateTag
import com.flxrs.dankchat.preferences.developer.DeveloperSettings
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import com.flxrs.dankchat.utils.extensions.INVISIBLE_CHAR
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ChatMessageSenderTest {
    @Test
    fun `repeated messages alternate without accumulating spaces`() = assertAlternating("forsenE forsenE", "forsenE  forsenE")

    @Test
    fun `single word messages alternate the invisible suffix`() = assertAlternating("forsenE", "forsenE $INVISIBLE_CHAR")

    @Test
    fun `action messages preserve the command separator`() = assertAlternating("/me forsenE forsenE", "/me forsenE  forsenE")

    @Test
    fun `intentional extra spaces are preserved`() = assertAlternating("forsenE  forsenE", "forsenE   forsenE")

    @Test
    fun `unique chat mode rotates invisible characters then increases their count`() = assertMessages(
        message = "forsenE forsenE forsenE",
        expected = listOf(
            "forsenE forsenE forsenE",
            "forsenE $INVISIBLE_CHAR forsenE forsenE",
            "forsenE forsenE $INVISIBLE_CHAR forsenE",
            "forsenE ${INVISIBLE_CHAR.repeat(2)} forsenE forsenE",
            "forsenE forsenE ${INVISIBLE_CHAR.repeat(2)} forsenE",
        ),
        uniqueModes = List(5) { true },
    )

    @Test
    fun `turning unique chat mode off restores alternating messages`() = assertMessages(
        message = "forsenE forsenE",
        expected = listOf("forsenE forsenE", "forsenE $INVISIBLE_CHAR forsenE", "forsenE ${INVISIBLE_CHAR.repeat(2)} forsenE", "forsenE forsenE", "forsenE  forsenE"),
        uniqueModes = listOf(false, true, true, false, false),
    )

    @Test
    fun `missing room state uses alternating messages`() = assertMessages(
        message = "forsenE forsenE",
        expected = listOf("forsenE forsenE", "forsenE  forsenE", "forsenE forsenE"),
        uniqueModes = List(3) { null },
    )

    @Test
    fun `unique chat mode without gaps increases the invisible suffix`() = assertMessages(
        message = "forsenE",
        expected = listOf("forsenE", "forsenE $INVISIBLE_CHAR", "forsenE ${INVISIBLE_CHAR.repeat(2)}"),
        uniqueModes = List(3) { true },
    )

    @Test
    fun `unique chat mode does not insert characters into the command separator`() = assertMessages(
        message = "/me forsenE forsenE",
        expected = listOf("/me forsenE forsenE", "/me forsenE $INVISIBLE_CHAR forsenE", "/me forsenE ${INVISIBLE_CHAR.repeat(2)} forsenE"),
        uniqueModes = List(3) { true },
    )

    private fun assertAlternating(
        message: String,
        bypassed: String,
    ) = assertMessages(
        message = message,
        expected = List(10) { if (it % 2 == 0) message else bypassed },
    )

    private fun assertMessages(
        message: String,
        expected: List<String>,
        uniqueModes: List<Boolean?> = List(expected.size) { false },
    ) = runTest {
        val connector = mockk<ChatConnector>()
        val processor = mockk<ChatEventProcessor>()
        val settings = mockk<DeveloperSettingsDataStore>()
        val channelRepository = mockk<ChannelRepository>()
        val sentMessages = mutableListOf<String>()
        val lastSent = mutableMapOf<String, String>()
        val lastTyped = mutableMapOf<String, String>()
        var uniqueMode: Boolean? = false
        every { channelRepository.getRoomState(any()) } answers {
            uniqueMode?.let {
                RoomState(firstArg<String>().toUserName(), "1".toUserId(), mapOf(RoomStateTag.R9K to if (it) 1 else 0))
            }
        }
        every { settings.current() } returns DeveloperSettings()
        every { processor.getLastMessage(any()) } answers { lastSent[firstArg()] }
        every { processor.getLastMessageForDisplay(any()) } answers { lastTyped[firstArg()] }
        every { processor.setLastMessage(any(), any(), any()) } answers {
            lastSent[firstArg()] = secondArg()
            lastTyped[firstArg()] = thirdArg()
        }
        coEvery { connector.sendRaw(any()) } answers { sentMessages.add(firstArg()) }
        val sender =
            ChatMessageSender(
                chatConnector = connector,
                helixApiClient = mockk(),
                channelRepository = channelRepository,
                authDataStore = mockk(),
                chatMessageRepository = mockk(relaxed = true),
                chatEventProcessor = processor,
                developerSettingsDataStore = settings,
            )
        val channel = "forsen".toUserName()

        uniqueModes.forEach { mode ->
            uniqueMode = mode
            sender.send(channel, message)
        }

        assertEquals(expected.map { "PRIVMSG #forsen :$it" }, sentMessages)
        assertEquals(message, lastTyped[channel.value])

        sender.send("Iore".toUserName(), message)
        assertEquals("PRIVMSG #Iore :$message", sentMessages.last())
        sender.send(channel, "different message")
        assertEquals("PRIVMSG #forsen :different message", sentMessages.last())
    }
}
