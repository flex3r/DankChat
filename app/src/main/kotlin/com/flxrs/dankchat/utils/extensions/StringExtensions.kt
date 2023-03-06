package com.flxrs.dankchat.utils.extensions

private val emojiCodePoints = listOf(
    IntRange(0x00A9, 0x00AE),
    IntRange(0x200D, 0x2017),
    IntRange(0x201a, 0x3299),
    IntRange(0xFE00, 0xFE0F),
    IntRange(0x1F000, 0x1FAD6)
)

private val Int.isEmoji: Boolean
    get() = emojiCodePoints.any { this in it }

private val Int.isWhitespace: Boolean
    get() = Character.isWhitespace(this) || this == 0x3164

val Int.codePointAsString: String
    get() = String(Character.toChars(this))

// Removes duplicate whitespace from the string and additionally returns the positions of every removed whitespace
// "a  Kappa" -> 3-8 -> [2, 3]
// "a Kappa"  -> 2-7
fun String.removeDuplicateWhitespace(): Pair<String, List<Int>> {
    val stringBuilder = StringBuilder()
    var previousWhitespace = false
    val removedSpacesPositions = mutableListOf<Int>()
    var totalCharCount = 0

    codePoints { codePoint ->
        if (codePoint.isWhitespace) {
            when {
                previousWhitespace -> removedSpacesPositions += totalCharCount
                else               -> stringBuilder.appendCodePoint(codePoint)
            }

            previousWhitespace = true
        } else {
            previousWhitespace = false
            stringBuilder.appendCodePoint(codePoint)
        }

        totalCharCount++
    }

    return stringBuilder.toString() to removedSpacesPositions
}

// Adds extra space between every emoji group to support 3rd party emotes directly before/after emojis
// @badge-info=;badges=broadcaster/1,bits-charity/1;color=#00BCD4;display-name=flex3rs;emotes=521050:9-15,25-31;flags=;id=08649ff3-8fee-4200-8e06-c46bcdfb06e8;mod=0;room-id=73697410;subscriber=0;tmi-sent-ts=1575196101040;turbo=0;user-id=73697410;user-type= :flex3rs!flex3rs@flex3rs.tmi.twitch.tv PRIVMSG #flex3rs :🍓🍑🍊🍋🍍NaM forsenE 🍐🍏🐬🐳NaM forsenE
fun String.appendSpacesBetweenEmojiGroup(): Pair<String, List<Int>> {
    val fixedContentBuilder = StringBuilder()
    var previousEmoji = false
    var previousCodepoint = 0
    val addedSpacesPositions = mutableListOf<Int>()
    var totalCharCount = 0

    codePoints { codePoint ->
        if (codePoint.isEmoji) {
            // emoji group starts
            if (totalCharCount != 0 && !previousEmoji && !previousCodepoint.isWhitespace) {
                fixedContentBuilder.append(" ")
                addedSpacesPositions += totalCharCount
            }

            previousEmoji = true
        } else if (previousEmoji) {
            //emoji group ends
            if (!codePoint.isWhitespace) {
                fixedContentBuilder.append(" ")
                addedSpacesPositions += totalCharCount
            }

            previousEmoji = false
        }

        totalCharCount += Character.charCount(codePoint)
        previousCodepoint = codePoint
        fixedContentBuilder.appendCodePoint(codePoint)
    }

    return fixedContentBuilder.toString() to addedSpacesPositions
}

inline fun String.codePoints(block: (Int) -> Unit) {
    var i = 0
    while (i < this.length) {
        val c1: Char = this[i++]
        if (!Character.isHighSurrogate(c1) || i >= this.length) {
            block(c1.code)
        } else {
            val c2: Char = this[i]
            if (Character.isLowSurrogate(c2)) {
                i++
                block(Character.toCodePoint(c1, c2))
            } else {
                block(c1.code)
            }
        }
    }
}

val String.supplementaryCodePointPositions: List<Int>
    get() {
        val positions = mutableListOf<Int>()
        var offset = 0
        var index = 0
        while (offset < length) {
            val codepoint = codePointAt(offset)
            if (Character.isSupplementaryCodePoint(codepoint)) {
                positions += offset - index
                index++
            }
            offset += Character.charCount(codepoint)
        }

        return positions
    }

val String.withoutOAuthPrefix: String
    get() = removePrefix("oauth:")

val String.withTrailingSlash: String
    get() = when {
        endsWith('/') -> this
        else          -> "$this/"
    }

val String.withTrailingSpace: String
    get() = when {
        isNotBlank() && !endsWith(" ") -> "$this "
        else                           -> this
    }

val INVISIBLE_CHAR = 0x000E0000.codePointAsString
val String.withoutInvisibleChar: String
    get() = trimEnd().removeSuffix(INVISIBLE_CHAR).trimEnd()

inline fun CharSequence.indexOfFirst(startIndex: Int = 0, predicate: (Char) -> Boolean): Int {
    for (index in startIndex.coerceAtLeast(0)..lastIndex) {
        if (predicate(this[index])) {
            return index
        }
    }

    return -1
}
