package com.flxrs.dankchat.data.repo.chat

import com.flxrs.dankchat.data.toUserName
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
    fun `extra space cycles through gaps then returns to the original`() = assertCycle(
        message = "forsenE forsenE forsenE",
        variants = listOf("forsenE forsenE forsenE", "forsenE  forsenE forsenE", "forsenE forsenE  forsenE"),
    )

    @Test
    fun `cycling treats consecutive spaces as one gap`() = assertCycle(
        message = "forsenE  forsenE forsenE",
        variants = listOf("forsenE  forsenE forsenE", "forsenE   forsenE forsenE", "forsenE  forsenE  forsenE"),
    )

    @Test
    fun `cycling skips the command separator`() = assertCycle(
        message = "/me forsenE forsenE forsenE",
        variants = listOf("/me forsenE forsenE forsenE", "/me forsenE  forsenE forsenE", "/me forsenE forsenE  forsenE"),
    )

    private fun assertAlternating(
        message: String,
        bypassed: String,
    ) = assertCycle(message, listOf(message, bypassed))

    private fun assertCycle(
        message: String,
        variants: List<String>,
    ) = runTest {
        val connector = mockk<ChatConnector>()
        val processor = mockk<ChatEventProcessor>()
        val settings = mockk<DeveloperSettingsDataStore>()
        val sentMessages = mutableListOf<String>()
        val lastSent = mutableMapOf<String, String>()
        val lastTyped = mutableMapOf<String, String>()
        every { settings.current() } returns DeveloperSettings()
        every { processor.getLastMessage(any()) } answers { lastSent[firstArg()] }
        every { processor.setLastMessage(any(), any(), any()) } answers {
            lastSent[firstArg()] = secondArg()
            lastTyped[firstArg()] = thirdArg()
        }
        coEvery { connector.sendRaw(any()) } answers { sentMessages.add(firstArg()) }
        val sender =
            ChatMessageSender(
                chatConnector = connector,
                helixApiClient = mockk(),
                channelRepository = mockk(),
                authDataStore = mockk(),
                chatMessageRepository = mockk(relaxed = true),
                chatEventProcessor = processor,
                developerSettingsDataStore = settings,
            )
        val channel = "forsen".toUserName()

        repeat(variants.size * 3) { sender.send(channel, message) }

        assertEquals(List(variants.size * 3) { "PRIVMSG #forsen :${variants[it % variants.size]}" }, sentMessages)
        assertEquals(message, lastTyped[channel.value])

        sender.send("Iore".toUserName(), message)
        assertEquals("PRIVMSG #Iore :$message", sentMessages.last())
        sender.send(channel, "different message")
        assertEquals("PRIVMSG #forsen :different message", sentMessages.last())
    }
}
