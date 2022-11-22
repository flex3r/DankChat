package com.flxrs.dankchat.data.irc

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


internal class IrcMessageTest {

    // examples from https://github.com/robotty/twitch-irc-rs

    @Test
    fun `parse timeout message`() {
        val msg = "@ban-duration=1;room-id=11148817;target-user-id=148973258;tmi-sent-ts=1594553828245 :tmi.twitch.tv CLEARCHAT #pajlada :fabzeef"
        val ircMessage = IrcMessage.parse(msg)

        assertEquals(expected = "CLEARCHAT", actual = ircMessage.command)
        assertEquals(expected = listOf("#pajlada", "fabzeef"), actual = ircMessage.params)
        assertEquals(expected = "tmi.twitch.tv", actual = ircMessage.prefix)
        assertEquals(expected = "1", actual = ircMessage.tags["ban-duration"] )
        assertEquals(expected = "11148817", actual = ircMessage.tags["room-id"] )
        assertEquals(expected = "148973258", actual = ircMessage.tags["target-user-id"] )
        assertEquals(expected = "1594553828245", actual = ircMessage.tags["tmi-sent-ts"] )
        assertEquals(expected = 4, actual = ircMessage.tags.size)

    }

    @Test
    fun `parse permaban message`() {
        val msg = "@room-id=11148817;target-user-id=70948394;tmi-sent-ts=1594561360331 :tmi.twitch.tv CLEARCHAT #pajlada :weeb123"
        val ircMessage = IrcMessage.parse(msg)

        assertEquals(expected = "CLEARCHAT", actual = ircMessage.command)
        assertEquals(expected = listOf("#pajlada", "weeb123"), actual = ircMessage.params)
        assertEquals(expected = "tmi.twitch.tv", actual = ircMessage.prefix)
        assertEquals(expected = "11148817", actual = ircMessage.tags["room-id"] )
        assertEquals(expected = "70948394", actual = ircMessage.tags["target-user-id"] )
        assertEquals(expected = "1594561360331", actual = ircMessage.tags["tmi-sent-ts"] )
        assertEquals(expected = 3, actual = ircMessage.tags.size)
    }

    @Test
    fun `parse clear chat message`() {
        val msg = "@room-id=40286300;tmi-sent-ts=1594561392337 :tmi.twitch.tv CLEARCHAT #randers"
        val ircMessage = IrcMessage.parse(msg)

        assertEquals(expected = "CLEARCHAT", actual = ircMessage.command)
        assertEquals(expected = listOf("#randers"), actual = ircMessage.params)
        assertEquals(expected = "tmi.twitch.tv", actual = ircMessage.prefix)
        assertEquals(expected = "40286300", actual = ircMessage.tags["room-id"])
        assertEquals(expected = "1594561392337", actual = ircMessage.tags["tmi-sent-ts"])
        assertEquals(expected = 2, actual = ircMessage.tags.size)
    }
}