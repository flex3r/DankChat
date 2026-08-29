package com.flxrs.dankchat.ui.chat.messages.common

import android.util.Patterns
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import kotlinx.collections.immutable.ImmutableList

private val DISALLOWED_URL_CHARS = """<>\{}|^"`""".toSet()

@Immutable
data class LinkUi(
    val start: Int,
    val end: Int,
    val url: String,
)

// Runs the heavy url pattern once per message off the main thread, the resulting ranges are
// consumed by the message composables
fun findLinks(text: String): List<LinkUi> {
    val matcher = Patterns.WEB_URL.matcher(text)
    val links = mutableListOf<LinkUi>()

    while (matcher.find()) {
        val start = matcher.start()
        var end = matcher.end()

        val prevChar = if (start > 0) text[start - 1] else null
        if (prevChar != null && !prevChar.isWhitespace()) {
            continue
        }

        while (end < text.length) {
            val c = text[end]
            if (c.isWhitespace() || c in DISALLOWED_URL_CHARS) {
                break
            }
            end++
        }

        val rawUrl = text.substring(start, end)
        val url =
            when {
                rawUrl.contains("://") -> rawUrl
                else -> "https://$rawUrl"
            }

        links += LinkUi(start = start, end = end, url = url)
    }

    return links
}

// Appends a segment of the linkified text, [segmentStart] is the segment's offset in the text
// the [links] were found in
fun AnnotatedString.Builder.appendWithLinks(
    text: String,
    segmentStart: Int,
    links: ImmutableList<LinkUi>,
    linkColor: Color,
) = appendWithLinks(text, segmentStart, links, linkColor, emptyList())

internal fun AnnotatedString.Builder.appendWithLinks(
    text: String,
    segmentStart: Int,
    links: ImmutableList<LinkUi>,
    linkColor: Color,
    usernameMentions: List<ResolvedUsernameMention>,
) {
    val segmentEnd = segmentStart + text.length
    val ranges =
        buildList {
            links.forEach { link ->
                if (link.start >= segmentStart && link.end <= segmentEnd) {
                    add(StyledTextRange.Link(link.start, link.end, link.url))
                }
            }
            usernameMentions.forEach { mention ->
                if (mention.start >= segmentStart && mention.end <= segmentEnd) {
                    add(
                        StyledTextRange.UsernameMention(
                            mention.start,
                            mention.end,
                            mention.color,
                            mention.isBold,
                            mention.userAnnotation,
                        ),
                    )
                }
            }
        }.sortedBy { it.start }

    var lastIndex = 0
    ranges.forEach { range ->
        val start = range.start - segmentStart
        val end = range.end - segmentStart
        if (start < lastIndex || end > text.length) {
            return@forEach
        }

        if (start > lastIndex) {
            append(text.substring(lastIndex, start))
        }

        when (range) {
            is StyledTextRange.Link -> {
                pushStringAnnotation(tag = URL_ANNOTATION_TAG, annotation = range.url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(text.substring(start, end))
                }
                pop()
            }

            is StyledTextRange.UsernameMention -> {
                pushStringAnnotation(tag = MENTIONED_USER_ANNOTATION_TAG, annotation = range.userAnnotation)
                withStyle(
                    SpanStyle(
                        color = range.color ?: Color.Unspecified,
                        fontWeight = if (range.isBold) FontWeight.Bold else null,
                    ),
                ) {
                    append(text.substring(start, end))
                }
                pop()
            }
        }

        lastIndex = end
    }

    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

private sealed interface StyledTextRange {
    val start: Int
    val end: Int

    data class Link(
        override val start: Int,
        override val end: Int,
        val url: String,
    ) : StyledTextRange

    data class UsernameMention(
        override val start: Int,
        override val end: Int,
        val color: Color?,
        val isBold: Boolean,
        val userAnnotation: String,
    ) : StyledTextRange
}

fun AnnotatedString.Builder.appendWithLinks(
    text: String,
    linkColor: Color,
    previousChar: Char? = null,
) {
    val matcher = Patterns.WEB_URL.matcher(text)
    var lastIndex = 0

    while (matcher.find()) {
        val start = matcher.start()
        var end = matcher.end()

        val prevChar = if (start > 0) text[start - 1] else previousChar
        if (prevChar != null && !prevChar.isWhitespace()) {
            continue
        }

        var fixedEnd = end
        while (fixedEnd < text.length) {
            val c = text[fixedEnd]
            if (c.isWhitespace() || c in DISALLOWED_URL_CHARS) {
                break
            }
            fixedEnd++
        }
        end = fixedEnd

        val rawUrl = text.substring(start, end)
        val url =
            when {
                rawUrl.contains("://") -> rawUrl
                else -> "https://$rawUrl"
            }

        if (start > lastIndex) {
            append(text.substring(lastIndex, start))
        }

        pushStringAnnotation(tag = URL_ANNOTATION_TAG, annotation = url)
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append(rawUrl)
        }
        pop()

        lastIndex = end
    }

    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

fun extractUrls(text: String): List<String> {
    val urls = mutableListOf<String>()
    val matcher = Patterns.WEB_URL.matcher(text)

    while (matcher.find()) {
        val start = matcher.start()
        var end = matcher.end()

        val prevChar = if (start > 0) text[start - 1] else null
        if (prevChar != null && !prevChar.isWhitespace()) {
            continue
        }

        var fixedEnd = end
        while (fixedEnd < text.length) {
            val c = text[fixedEnd]
            if (c.isWhitespace() || c in DISALLOWED_URL_CHARS) {
                break
            }
            fixedEnd++
        }
        end = fixedEnd

        val rawUrl = text.substring(start, end)
        val url = when {
            rawUrl.contains("://") -> rawUrl
            else -> "https://$rawUrl"
        }
        urls += url
    }

    return urls
}

const val URL_ANNOTATION_TAG = "URL"
