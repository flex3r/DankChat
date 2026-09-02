package com.flxrs.dankchat.data.twitch.message

import java.net.URI

data class TwitchGif(
    val id: String,
    val url: String,
    val altText: String,
    /** Inclusive UTF-16 range relative to the displayed message. */
    val position: IntRange,
)

data class TwitchGifData(
    /** Message text before emote normalisation. */
    val message: String,
    val gifs: List<TwitchGif>,
)

internal fun parseTwitchGifTag(
    message: String,
    tag: String,
): List<TwitchGif> {
    if (tag.isEmpty()) return emptyList()

    val parsed =
        tag
            .split(',')
            .mapNotNull { entry ->
                val parts = entry.split('|', limit = 3)
                if (parts.size != 3) return@mapNotNull null

                val (startText, endText) = parts[0].split('-', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                val start = startText.toIntOrNull() ?: return@mapNotNull null
                val end = endText.toIntOrNull() ?: return@mapNotNull null
                val id = parts[1]
                val url = parts[2]
                if (start < 0 || end < start || id.isEmpty() || !url.isValidHttpsUrl()) return@mapNotNull null

                val position = message.codePointRangeToUtf16(start..end) ?: return@mapNotNull null

                TwitchGif(
                    id = id,
                    url = url,
                    altText = message.substring(position),
                    position = position,
                )
            }.sortedBy { it.position.first }

    var previousEnd = -1
    return parsed.filter { gif ->
        val accepted = gif.position.first > previousEnd
        if (accepted) previousEnd = gif.position.last
        accepted
    }
}

private fun String.isValidHttpsUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
}.getOrDefault(false)

internal fun String.toTwitchGifLoadUrl(): String {
    val uri = runCatching { URI(this) }.getOrNull() ?: return this
    val host = uri.host?.lowercase() ?: return this
    if (host != "giphy.com" && !host.endsWith(".giphy.com")) return this

    val pathEnd = indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: length
    val path = substring(0, pathEnd)
    if (!path.endsWith("/giphy.gif")) return this

    val rewritten = path.removeSuffix("giphy.gif") + "200.webp" + substring(pathEnd)
    return GIPHY_RID_QUERY_REGEX.replace(rewritten) { match -> "${match.groupValues[1]}200.webp" }
}

private val GIPHY_RID_QUERY_REGEX = Regex("([?&]rid=)giphy\\.gif(?=(&|#|$))")

private fun String.codePointRangeToUtf16(range: IntRange): IntRange? {
    val count = codePointCount(0, length)
    if (range.first < 0 || range.last < range.first || range.last >= count) return null

    val start = offsetByCodePoints(0, range.first)
    val endExclusive = offsetByCodePoints(0, range.last + 1)
    return start until endExclusive
}

internal data class PositionedTextEdit(
    val start: Int,
    val endExclusive: Int,
    val replacementLength: Int,
)

internal enum class PositionedTextEditOverlapPolicy {
    Drop,
    PreserveContainedEdits,
}

internal fun List<TwitchGif>.applyTextEdits(
    edits: List<PositionedTextEdit>,
    policy: PositionedTextEditOverlapPolicy = PositionedTextEditOverlapPolicy.Drop,
): List<TwitchGif> = mapNotNull { gif ->
    val start = gif.position.first
    val endExclusive = gif.position.last + 1
    var shift = 0
    var containedDelta = 0

    edits.sortedBy { it.start }.forEach { edit ->
        if (edit.start < 0 || edit.endExclusive < edit.start) return@mapNotNull null
        val delta = edit.replacementLength - (edit.endExclusive - edit.start)
        when {
            edit.endExclusive <= start -> shift += delta

            edit.start >= endExclusive -> Unit

            policy == PositionedTextEditOverlapPolicy.PreserveContainedEdits &&
                edit.start >= start && edit.endExclusive <= endExclusive -> containedDelta += delta

            else -> return@mapNotNull null
        }
    }

    val adjustedStart = start + shift
    val adjustedEndExclusive = endExclusive + shift + containedDelta
    if (adjustedStart < 0 || adjustedEndExclusive <= adjustedStart) {
        null
    } else {
        gif.copy(position = adjustedStart..adjustedEndExclusive - 1)
    }
}
