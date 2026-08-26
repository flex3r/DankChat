package com.flxrs.dankchat.ui.main.dialog

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class AddChannelDialogTest {
    @Test
    fun `single channel name is supported`() {
        assertEquals(listOf("channel"), parseChannelNames("channel").map { it.value })
    }

    @Test
    fun `channel names can be separated by whitespace or commas`() {
        val channels = parseChannelNames("one, two\nthree\tfour")

        assertEquals(listOf("one", "two", "three", "four"), channels.map { it.value })
    }

    @Test
    fun `channel names are deduplicated case insensitively`() {
        val channels = parseChannelNames("One one, TWO two")

        assertEquals(listOf("One", "TWO"), channels.map { it.value })
    }
}
