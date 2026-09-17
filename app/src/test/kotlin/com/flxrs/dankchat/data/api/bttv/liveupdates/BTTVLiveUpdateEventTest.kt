package com.flxrs.dankchat.data.api.bttv.liveupdates

import com.flxrs.dankchat.data.UserId
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

internal class BTTVLiveUpdateEventTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes added emote`() {
        val event = json.decodeBTTVLiveUpdateEvent(
            """{"name":"emote_create","data":{"channel":"twitch:123","emote":{"id":"abc","code":"OMEGALUL","user":{"displayName":"Forsen"},"extra":true}}}""",
        )

        val added = assertIs<BTTVLiveUpdateEvent.EmoteAdded>(event)
        assertEquals(UserId("123"), added.channelId)
        assertEquals("abc", added.emote.id)
        assertEquals("OMEGALUL", added.emote.code)
    }

    @Test
    fun `decodes updated emote without user`() {
        val event = json.decodeBTTVLiveUpdateEvent(
            """{"name":"emote_update","data":{"channel":"twitch:123","emote":{"id":"abc","code":"OMEGALUL2"}}}""",
        )

        val updated = assertIs<BTTVLiveUpdateEvent.EmoteUpdated>(event)
        assertEquals("OMEGALUL2", updated.emote.code)
        assertNull(updated.emote.user)
    }

    @Test
    fun `decodes removed emote`() {
        val event = json.decodeBTTVLiveUpdateEvent(
            """{"name":"emote_delete","data":{"channel":"twitch:123","emoteId":"abc"}}""",
        )

        val removed = assertIs<BTTVLiveUpdateEvent.EmoteRemoved>(event)
        assertEquals(UserId("123"), removed.channelId)
        assertEquals("abc", removed.emoteId)
    }

    @Test
    fun `rejects malformed channel`() {
        val event = json.decodeBTTVLiveUpdateEvent(
            """{"name":"emote_delete","data":{"channel":"youtube:123","emoteId":"abc"}}""",
        )

        assertNull(event)
    }
}
