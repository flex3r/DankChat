package com.flxrs.dankchat.data.repo.emote

import com.flxrs.dankchat.data.api.dankchat.DankChatApiClient
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmoteType
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals


@ExtendWith(MockKExtension::class)
internal class EmoteRepositoryTest {

    @MockK
    lateinit var dankchatApiClient: DankChatApiClient

    @MockK
    lateinit var preferences: DankChatPreferenceStore

    @InjectMockKs
    lateinit var emoteRepository: EmoteRepository

    @Test
    fun `overlay emotes are not moved if regular text is in-between`() {
        val message = "FeelsDankMan asd cvHazmat RainTime"
        val emotes = listOf(
            ChatMessageEmote(position = 0..12, url = "asd", id = "1", code = "FeelsDankMan", scale = 1, type = ChatMessageEmoteType.TwitchEmote),
            ChatMessageEmote(position = 17..25, url = "asd", id = "1", code = "cvHazmat", scale = 1, type = ChatMessageEmoteType.TwitchEmote, isOverlayEmote = true),
            ChatMessageEmote(position = 26..34, url = "asd", id = "1", code = "cvHazmat", scale = 1, type = ChatMessageEmoteType.TwitchEmote, isOverlayEmote = true),
        )
        val (resultMessage, resultEmotes) = emoteRepository.adjustOverlayEmotes(message, emotes)

        assertEquals(expected = message, actual = resultMessage)
        assertEquals(expected = emotes, actual = resultEmotes)
    }

    @Test
    fun `overlay emotes are moved if no regular text is in-between`() {
        val message = "FeelsDankMan cvHazmat RainTime"
        val emotes = listOf(
            ChatMessageEmote(position = 0..12, url = "asd", id = "1", code = "FeelsDankMan", scale = 1, type = ChatMessageEmoteType.TwitchEmote),
            ChatMessageEmote(position = 13..21, url = "asd", id = "1", code = "cvHazmat", scale = 1, type = ChatMessageEmoteType.TwitchEmote, isOverlayEmote = true),
            ChatMessageEmote(position = 22..30, url = "asd", id = "1", code = "cvHazmat", scale = 1, type = ChatMessageEmoteType.TwitchEmote, isOverlayEmote = true),
        )
        val expectedMessage = "FeelsDankMan " // KKona
        val expectedEmotes = listOf(
            ChatMessageEmote(position = 0..12, url = "asd", id = "1", code = "FeelsDankMan", scale = 1, type = ChatMessageEmoteType.TwitchEmote),
            ChatMessageEmote(position = 0..12, url = "asd", id = "1", code = "cvHazmat", scale = 1, type = ChatMessageEmoteType.TwitchEmote, isOverlayEmote = true),
            ChatMessageEmote(position = 0..12, url = "asd", id = "1", code = "cvHazmat", scale = 1, type = ChatMessageEmoteType.TwitchEmote, isOverlayEmote = true),
        )

        val (resultMessage, resultEmotes) = emoteRepository.adjustOverlayEmotes(message, emotes)

        assertEquals(expected = expectedMessage, actual = resultMessage)
        assertEquals(expected = expectedEmotes, actual = resultEmotes)
    }
}
