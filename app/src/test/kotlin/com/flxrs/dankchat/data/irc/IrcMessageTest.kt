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

    @Test
    fun `parse delete chat message`() {
        val msg = "@login=alazymeme;room-id=;target-msg-id=3c92014f-340a-4dc3-a9c9-e5cf182f4a84;tmi-sent-ts=1594561955611 :tmi.twitch.tv CLEARMSG #pajlada :NIGHT CUNT"
        val ircMessage = IrcMessage.parse(msg)

        assertEquals(expected = "CLEARMSG", actual = ircMessage.command)
        assertEquals(expected = listOf("#pajlada", "NIGHT CUNT"), actual = ircMessage.params)
        assertEquals(expected = "tmi.twitch.tv", actual = ircMessage.prefix)
        assertEquals(expected = "alazymeme", actual = ircMessage.tags["login"])
        assertEquals(expected = "", actual = ircMessage.tags["room-id"])
        assertEquals(expected = "3c92014f-340a-4dc3-a9c9-e5cf182f4a84", actual = ircMessage.tags["target-msg-id"])
        assertEquals(expected = "1594561955611", actual = ircMessage.tags["tmi-sent-ts"])
        assertEquals(expected = 4, actual = ircMessage.tags.size)
    }

    @Test
    fun `parse global user state message with color`() {
        val msg = "@badge-info=;badges=;color=#19E6E6;display-name=randers;emote-sets=0,42,237;user-id=40286300;user-type= :tmi.twitch.tv GLOBALUSERSTATE"
        val ircMessage = IrcMessage.parse(msg)

        assertEquals(expected = "GLOBALUSERSTATE", actual = ircMessage.command)
        assertEquals(expected = listOf(), actual = ircMessage.params)
        assertEquals(expected = "tmi.twitch.tv", actual = ircMessage.prefix)
        assertEquals(expected = "", actual = ircMessage.tags["badge-info"])
        assertEquals(expected = "", actual = ircMessage.tags["badges"])
        assertEquals(expected = "#19E6E6", actual = ircMessage.tags["color"])
        assertEquals(expected = "randers", actual = ircMessage.tags["display-name"])
        assertEquals(expected = "0,42,237", actual = ircMessage.tags["emote-sets"])
        assertEquals(expected = "40286300", actual = ircMessage.tags["user-id"])
        assertEquals(expected = "", actual = ircMessage.tags["user-type"])
        assertEquals(expected = 7, actual = ircMessage.tags.size)
    }

    @Test
    fun `parse global user state message with badges`() {
        val msg = "@badge-info=;badges=premium/1;color=;display-name=randers;emote-sets=;user-id=40286300;user-type= :tmi.twitch.tv GLOBALUSERSTATE"
        val ircMessage = IrcMessage.parse(msg)

        assertEquals(expected = "GLOBALUSERSTATE", actual = ircMessage.command)
        assertEquals(expected = listOf(), actual = ircMessage.params)
        assertEquals(expected = "tmi.twitch.tv", actual = ircMessage.prefix)
        assertEquals(expected = "", actual = ircMessage.tags["badge-info"])
        assertEquals(expected = "premium/1", actual = ircMessage.tags["badges"])
        assertEquals(expected = "", actual = ircMessage.tags["color"])
        assertEquals(expected = "randers", actual = ircMessage.tags["display-name"])
        assertEquals(expected = "", actual = ircMessage.tags["emote-sets"])
        assertEquals(expected = "40286300", actual = ircMessage.tags["user-id"])
        assertEquals(expected = "", actual = ircMessage.tags["user-type"])
        assertEquals(expected = 7, actual = ircMessage.tags.size)
    }

    @Test
    fun `parse plain global user state message`() {
        val msg = "@badge-info=;badges=;color=;display-name=randers811;emote-sets=0;user-id=553170741;user-type= :tmi.twitch.tv GLOBALUSERSTATE"
        val ircMessage = IrcMessage.parse(msg)

        assertEquals(expected = "GLOBALUSERSTATE", actual = ircMessage.command)
        assertEquals(expected = listOf(), actual = ircMessage.params)
        assertEquals(expected = "tmi.twitch.tv", actual = ircMessage.prefix)
        assertEquals(expected = "", actual = ircMessage.tags["badge-info"])
        assertEquals(expected = "", actual = ircMessage.tags["badges"])
        assertEquals(expected = "", actual = ircMessage.tags["color"])
        assertEquals(expected = "randers811", actual = ircMessage.tags["display-name"])
        assertEquals(expected = "0", actual = ircMessage.tags["emote-sets"])
        assertEquals(expected = "553170741", actual = ircMessage.tags["user-id"])
        assertEquals(expected = "", actual = ircMessage.tags["user-type"])
        assertEquals(expected = 7, actual = ircMessage.tags.size)
    }
}