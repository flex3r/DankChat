package com.flxrs.dankchat.data.twitch.message

import java.text.BreakIterator
import java.util.Locale

private const val MIN_ART_CELLS = 40

/**
 * Checks whether this message contains enough Unicode symbols to be ASCII art.
 */
internal fun String.isAsciiArt(): Boolean = length >= MIN_ART_CELLS && hasMinArtCodePoints() && hasMinArtCells()

// Every art cell contains at least one art code point, so this cheap scan rejects regular messages
private fun String.hasMinArtCodePoints(): Boolean {
    var count = 0
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        if (isArtCodePoint(codePoint) && ++count >= MIN_ART_CELLS) {
            return true
        }
        index += Character.charCount(codePoint)
    }
    return false
}

private fun String.hasMinArtCells(): Boolean {
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
    boundaries.setText(this)

    var cells = 0
    var start = boundaries.first()
    var end = boundaries.next()
    while (end != BreakIterator.DONE) {
        if (containsArtCodePoint(start, end) && ++cells >= MIN_ART_CELLS) {
            return true
        }
        start = end
        end = boundaries.next()
    }
    return false
}

private fun String.containsArtCodePoint(
    start: Int,
    end: Int,
): Boolean {
    var index = start
    while (index < end) {
        val codePoint = codePointAt(index)
        if (isArtCodePoint(codePoint)) {
            return true
        }
        index += Character.charCount(codePoint)
    }
    return false
}

// Emoji, flags and skin tone modifiers start at U+1F000 and are too inconsistent across platforms to align as art
private const val FIRST_EMOJI_CODE_POINT = 0x1F000

private fun isArtCodePoint(codePoint: Int): Boolean = when {
    codePoint >= FIRST_EMOJI_CODE_POINT -> false

    else -> when (Character.getType(codePoint)) {
        Character.OTHER_SYMBOL.toInt(),
        Character.MODIFIER_SYMBOL.toInt(),
        -> true

        else -> false
    }
}
