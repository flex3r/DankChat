package com.flxrs.dankchat.data.twitch.message

import java.net.URI

/**
 * A GIF from the `gifs` tag. Like the `emotes` tag, [position] is an inclusive code point range into the
 * original message, it is converted together with Twitch emote positions during emote parsing.
 */
data class TwitchGifWithPosition(
    val id: String,
    val url: String,
    val position: IntRange,
)

data class TwitchGif(
    val id: String,
    val url: String,
    /** The message text the GIF replaces. */
    val altText: String,
    /** Inclusive UTF-16 range in the displayed message. */
    val position: IntRange,
)

// The `gifs` tag contains comma-separated `start-end|id|url` entries:
// https://dev.twitch.tv/docs/chat/irc/#:~:text=its%20succeeding%20whitespace.-,gifs,-Comma%2Dseparated%20list
internal fun parseTwitchGifTag(tag: String): List<TwitchGifWithPosition> {
    if (tag.isEmpty()) {
        return emptyList()
    }

    return tag.split(',').mapNotNull { entry ->
        val parts = entry.split('|', limit = 3)
        val range = parts.firstOrNull()?.split('-', limit = 2)
        val start = range?.getOrNull(0)?.toIntOrNull()
        val end = range?.getOrNull(1)?.toIntOrNull()
        val id = parts.getOrNull(1)
        val url = parts.getOrNull(2)
        when {
            start == null || end == null || start < 0 || end < start -> null
            id.isNullOrEmpty() || url == null || !url.isValidHttpsUrl() -> null
            else -> TwitchGifWithPosition(id = id, url = url, position = start..end)
        }
    }
}

private fun String.isValidHttpsUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
}.getOrDefault(false)

// Giphy provides GIF, MP4 and WebP versions. We use the 200px-high `200.webp` because
// WebP is more size efficient than GIF.
// Giphy's MP4 files are smaller than WebP, but do not support transparency.
// https://developers.giphy.com/docs/api/schema/image-object/
internal fun String.toTwitchGifLoadUrl(): String {
    val uri = runCatching { URI(this) }.getOrNull() ?: return this
    val host = uri.host?.lowercase() ?: return this
    if (host != "giphy.com" && !host.endsWith(".giphy.com")) {
        return this
    }

    val pathEnd = indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: length
    val path = substring(0, pathEnd)
    if (!path.endsWith("/giphy.gif")) {
        return this
    }

    val rewritten = path.removeSuffix("giphy.gif") + "200.webp" + substring(pathEnd)
    return GIPHY_RID_QUERY_REGEX.replace(rewritten) { match -> "${match.groupValues[1]}200.webp" }
}

private val GIPHY_RID_QUERY_REGEX = Regex("([?&]rid=)giphy\\.gif(?=(&|#|$))")
