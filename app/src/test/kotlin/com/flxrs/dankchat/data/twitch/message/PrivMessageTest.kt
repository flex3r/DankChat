package com.flxrs.dankchat.data.twitch.message

import com.flxrs.dankchat.data.irc.IrcMessage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Suppress("MaxLineLength")
internal class PrivMessageTest {
    // recent-messages serializes an unset color as a key-only tag (`color`) instead of `color=`.
    // The message must still parse into a PrivMessage with an empty color tag rather than being
    // dropped from history. (Color.parseColor is stubbed under returnDefaultValues, so the parsed
    // Int can't be asserted here — the production null mapping lives in parseColorOrNull.)
    @Test
    fun `parse privmsg with key-only color tag is not dropped`() {
        val msg =
            "@badge-info=;badges=;color;display-name=ColorlessUser;emotes=;first-msg=0;flags=;historical=1;id=e9d998c3-36f1-430f-89ec-6b887c28af36;mod=0;rm-received-ts=1781341934600;room-id=11148817;subscriber=0;tmi-sent-ts=1781341934595;turbo=0;user-id=29803735;user-type= :colorlessuser!colorlessuser@colorlessuser.tmi.twitch.tv PRIVMSG #pajlada :hello"
        val message = Message.parse(IrcMessage.parse(msg)) { null }

        val privMessage = assertIs<PrivMessage>(message)
        assertEquals(expected = "", actual = privMessage.tags["color"])
        assertEquals(expected = "colorlessuser", actual = privMessage.name.value)
        assertEquals(expected = "hello", actual = privMessage.message)
    }

    // Reply messages from a colorless user place the key-only color tag directly before the reply
    // thread tags, matching the live recent-messages payload reported in the field.
    @Test
    fun `parse reply privmsg with key-only color tag is not dropped`() {
        val msg =
            "@badge-info=;badges=;color;display-name=ColorlessUser;emotes=;flags=;id=abc12345-6789-0def-1234-567890abcdef;mod=0;reply-thread-parent-display-name=Pajlada;reply-thread-parent-msg-id=parent-msg-id;reply-thread-parent-user-id=11148817;reply-thread-parent-user-login=pajlada;room-id=11148817;subscriber=0;tmi-sent-ts=1781341934595;turbo=0;user-id=29803735;user-type= :colorlessuser!colorlessuser@colorlessuser.tmi.twitch.tv PRIVMSG #pajlada :@Pajlada hello"
        val message = Message.parse(IrcMessage.parse(msg)) { null }

        val privMessage = assertIs<PrivMessage>(message)
        assertEquals(expected = "", actual = privMessage.tags["color"])
        assertEquals(expected = "colorlessuser", actual = privMessage.name.value)
    }

    @Test
    fun `parse action privmsg positions gif against stripped action body`() {
        val msg =
            "@badge-info=;badges=;color=#000000;display-name=Forsen;emotes=;gifs=0-4|gif-id|https://example.com/a.gif?x=1&y=2;id=gif-message;room-id=1;user-id=2 :forsen!forsen@forsen.tmi.twitch.tv PRIVMSG #forsen :\u0001ACTION [GIF]\u0001"

        val privMessage = assertIs<PrivMessage>(Message.parse(IrcMessage.parse(msg)) { null })

        assertEquals("[GIF]", privMessage.message)
        assertEquals("[GIF]", privMessage.gifs.single().altText)
        assertEquals(0..4, privMessage.gifs.single().position)
        assertEquals("https://example.com/a.gif?x=1&y=2", privMessage.gifs.single().url)
    }
}
