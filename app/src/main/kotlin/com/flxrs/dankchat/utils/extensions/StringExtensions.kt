package com.flxrs.dankchat.utils.extensions

private val emojiCodePoints = listOf(
    IntRange(0x00A9, 0x00A9),
    IntRange(0x00AE, 0x00AE),
    IntRange(0x203C, 0x203C),
    IntRange(0x2049, 0x2049),
    IntRange(0x20E3, 0x20E3),
    IntRange(0x2122, 0x2122),
    IntRange(0x2139, 0x2139),
    IntRange(0x2194, 0x2199),
    IntRange(0x21A9, 0x21AA),
    IntRange(0x231A, 0x231A),
    IntRange(0x231B, 0x231B),
    IntRange(0x2328, 0x2328),
    IntRange(0x23CF, 0x23CF),
    IntRange(0x23E9, 0x23F3),
    IntRange(0x23F8, 0x23FA),
    IntRange(0x24C2, 0x24C2),
    IntRange(0x25AA, 0x25AA),
    IntRange(0x25AB, 0x25AB),
    IntRange(0x25B6, 0x25B6),
    IntRange(0x25C0, 0x25C0),
    IntRange(0x25FB, 0x25FE),
    IntRange(0x2600, 0x27EF),
    IntRange(0x2934, 0x2934),
    IntRange(0x2935, 0x2935),
    IntRange(0x2B00, 0x2BFF),
    IntRange(0x3030, 0x3030),
    IntRange(0x303D, 0x303D),
    IntRange(0x3297, 0x3297),
    IntRange(0x3299, 0x3299),
    IntRange(0x1F000, 0x1F02F),
    IntRange(0x1F0A0, 0x1F0FF),
    IntRange(0x1F100, 0x1F64F),
    IntRange(0x1F680, 0x1F6FF),
    IntRange(0x1F910, 0x1F96B),
    IntRange(0x1F980, 0x1F9E0)
)

private val Int.isEmoji: Boolean
    get() = emojiCodePoints.any { range -> this in range }

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
            if (totalCharCount != 0 && !previousEmoji && !Character.isWhitespace(previousCodepoint)) {
                fixedContentBuilder.append(" ")
                addedSpacesPositions.add(totalCharCount)
            }

            previousEmoji = true
        } else if (previousEmoji) {
            //emoji group ends
            if (!Character.isWhitespace(codePoint)) {
                fixedContentBuilder.append(" ")
                addedSpacesPositions.add(totalCharCount)
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
    while (i < length) {
        val c1: Char = get(i++)
        if (!Character.isHighSurrogate(c1) || i >= length) {
            block(c1.toInt())
        } else {
            val c2: Char = get(i)
            if (Character.isLowSurrogate(c2)) {
                i++
                block(Character.toCodePoint(c1, c2))
            } else {
                block(c1.toInt())
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
                positions.add(offset - index)
                index++
            }
            offset += Character.charCount(codepoint)
        }

        return positions
    }
