package com.flxrs.dankchat.data.twitch.message

import java.text.BreakIterator
import java.util.Locale

private const val MIN_ART_CELLS = 40

/**
 * Checks whether this message contains enough Unicode symbols to be ASCII art.
 */
internal fun String.isAsciiArt(): Boolean = countArtCells() >= MIN_ART_CELLS

private fun String.countArtCells(): Int {
    val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
    boundaries.setText(this)

    var cells = 0
    var start = boundaries.first()
    var end = boundaries.next()
    while (end != BreakIterator.DONE) {
        if (substring(start, end).codePoints().anyMatch(::isArtCodePoint)) {
            cells++
        }
        start = end
        end = boundaries.next()
    }
    return cells
}

private fun isArtCodePoint(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
    Character.OTHER_SYMBOL.toInt(),
    Character.MODIFIER_SYMBOL.toInt(),
    -> true

    else -> false
}
