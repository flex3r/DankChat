package com.flxrs.dankchat.data.twitch.message

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AsciiArtTest {
    @Test
    fun `rejects ambiguous messages`() {
        assertFalse("ordinary prose ⣿⣿⣿".isAsciiArt())

        val modifiedHand = "👉🏿"
        assertFalse((modifiedHand.repeat(10) + " " + modifiedHand.repeat(10)).isAsciiArt())
    }

    @Test
    fun `detects mixed Unicode art`() {
        val longText = "x".repeat(200)

        assertTrue((BRAILLE_SEGMENT + BRAILLE_SEGMENT).isAsciiArt())
        assertTrue((BLOCK_SEGMENT + " " + longText + " " + BLOCK_SEGMENT).isAsciiArt())

        val rowWithBrailleBlank = BRAILLE_SEGMENT + "⠀" + BRAILLE_SEGMENT
        assertTrue((BRAILLE_SEGMENT + " mixed text " + rowWithBrailleBlank).isAsciiArt())
    }

    @Test
    fun `detects emoji art by grapheme`() {
        val modifiedHand = "👉🏿"

        assertTrue((modifiedHand.repeat(20) + " " + modifiedHand.repeat(20)).isAsciiArt())
    }

    private companion object {
        const val BRAILLE_SEGMENT = "⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿"
        const val BLOCK_SEGMENT = "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
    }
}
