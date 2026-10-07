package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.ChatMessageUiState
import com.flxrs.dankchat.ui.chat.messages.common.MessageTextWithInlineContent
import com.flxrs.dankchat.ui.chat.messages.common.SimpleMessageContainer
import com.flxrs.dankchat.ui.chat.messages.common.appendInlineSpacer
import com.flxrs.dankchat.ui.chat.messages.common.appendWithLinks
import com.flxrs.dankchat.ui.chat.messages.common.launchCustomTab
import com.flxrs.dankchat.ui.chat.messages.common.parseUserAnnotation
import com.flxrs.dankchat.ui.chat.messages.common.rememberAdaptiveLinkColor
import com.flxrs.dankchat.ui.chat.messages.common.rememberAdaptiveTextColor
import com.flxrs.dankchat.ui.chat.messages.common.rememberBackgroundColor
import com.flxrs.dankchat.ui.chat.messages.common.rememberNormalizedColor
import com.flxrs.dankchat.ui.chat.messages.common.timestampSpanStyle
import com.flxrs.dankchat.utils.resolve
import kotlinx.collections.immutable.persistentListOf

/**
 * Renders a system message (connected, disconnected, emote loading failures, etc.)
 */
@Composable
fun SystemMessageComposable(
    message: ChatMessageUiState.SystemMessageUi,
    fontSize: Float,
    modifier: Modifier = Modifier,
) {
    SimpleMessageContainer(
        message = message.message.resolve(),
        timestamp = message.timestamp,
        fontSize = fontSize.sp,
        lightBackgroundColor = message.lightBackgroundColor,
        darkBackgroundColor = message.darkBackgroundColor,
        textAlpha = message.textAlpha,
        boldText = message.boldText,
        modifier = modifier,
    )
}

/**
 * Renders a notice message from Twitch
 */
@Composable
fun NoticeMessageComposable(
    message: ChatMessageUiState.NoticeMessageUi,
    fontSize: Float,
    modifier: Modifier = Modifier,
) {
    SimpleMessageContainer(
        message = message.message,
        timestamp = message.timestamp,
        fontSize = fontSize.sp,
        lightBackgroundColor = message.lightBackgroundColor,
        darkBackgroundColor = message.darkBackgroundColor,
        textAlpha = message.textAlpha,
        links = message.links,
        modifier = modifier,
    )
}

/**
 * Renders a user notice message (subscriptions, announcements, etc.)
 * The display name is highlighted with the user's color and is clickable to open the user popup.
 */
@Composable
fun UserNoticeMessageComposable(
    message: ChatMessageUiState.UserNoticeMessageUi,
    fontSize: Float,
    onUserClick: (userId: String?, userName: String, displayName: String, channel: String?, badges: List<BadgeUi>, isLongPress: Boolean) -> Unit,
    onMessageLongClick: (messageId: String, channel: String?, fullMessage: String) -> Unit,
    modifier: Modifier = Modifier,
    animateGifs: Boolean = true,
    highlightShape: Shape = RectangleShape,
) {
    val context = LocalPlatformContext.current
    val bgColor = rememberBackgroundColor(message.lightBackgroundColor, message.darkBackgroundColor)
    val textColor = rememberAdaptiveTextColor(bgColor)
    val linkColor = rememberAdaptiveLinkColor(bgColor)
    val timestampColor = rememberAdaptiveTextColor(bgColor)
    val nameColor = rememberNormalizedColor(message.rawNameColor, bgColor)

    val annotatedString =
        remember(message, textColor, nameColor, linkColor, timestampColor, fontSize) {
            buildAnnotatedString {
                // Timestamp
                if (message.timestamp.isNotEmpty()) {
                    withStyle(timestampSpanStyle(fontSize, timestampColor)) {
                        append(message.timestamp)
                    }
                    appendInlineSpacer(6.dp)
                }

                // Message text with colored display name
                val displayName = message.displayName
                val msgText = message.message
                val nameIndex =
                    when {
                        displayName.isNotEmpty() -> msgText.indexOf(displayName, ignoreCase = true)
                        else -> -1
                    }

                when {
                    nameIndex >= 0 -> {
                        // Text before name
                        if (nameIndex > 0) {
                            withStyle(SpanStyle(color = textColor)) {
                                appendWithLinks(msgText.substring(0, nameIndex), 0, message.links, linkColor)
                            }
                        }

                        // Colored username, clickable when we know the underlying login
                        withStyle(SpanStyle(color = nameColor)) {
                            val userName = message.userName
                            when {
                                userName != null -> {
                                    pushStringAnnotation(
                                        tag = "USER",
                                        annotation = "${message.userId?.value.orEmpty()}|${userName.value}|${message.displayName}|${message.channel.value}",
                                    )
                                    append(msgText.substring(nameIndex, nameIndex + displayName.length))
                                    pop()
                                }

                                else -> append(msgText.substring(nameIndex, nameIndex + displayName.length))
                            }
                        }

                        // Text after name
                        val afterIndex = nameIndex + displayName.length
                        if (afterIndex < msgText.length) {
                            withStyle(SpanStyle(color = textColor)) {
                                appendWithLinks(msgText.substring(afterIndex), afterIndex, message.links, linkColor)
                            }
                        }
                    }

                    else -> {
                        // No display name found, render as plain text
                        withStyle(SpanStyle(color = textColor)) {
                            appendWithLinks(msgText, 0, message.links, linkColor)
                        }
                    }
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .alpha(message.textAlpha)
                .background(bgColor, highlightShape)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        MessageTextWithInlineContent(
            annotatedString = annotatedString,
            badges = persistentListOf(),
            emotes = persistentListOf(),
            fontSize = fontSize,
            animateGifs = animateGifs,
            onEmoteClick = {},
            onTextClick = { offset ->
                val user = annotatedString.getStringAnnotations("USER", offset, offset).firstOrNull()
                val url = annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()

                when {
                    user != null -> parseUserAnnotation(user.item)?.let {
                        onUserClick(it.userId, it.userName, it.displayName, it.channel.orEmpty(), emptyList(), false)
                    }

                    url != null -> launchCustomTab(context, url.item)
                }
            },
            onTextLongClick = { offset ->
                val user = annotatedString.getStringAnnotations("USER", offset, offset).firstOrNull()

                when {
                    user != null -> parseUserAnnotation(user.item)?.let {
                        onUserClick(it.userId, it.userName, it.displayName, it.channel.orEmpty(), emptyList(), true)
                    }

                    else -> onMessageLongClick(message.id, message.channel.value, message.message)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Renders a date separator between messages from different days
 */
@Composable
fun DateSeparatorComposable(
    message: ChatMessageUiState.DateSeparatorUi,
    fontSize: Float,
    modifier: Modifier = Modifier,
) {
    SimpleMessageContainer(
        message = message.dateText,
        timestamp = message.timestamp,
        fontSize = fontSize.sp,
        lightBackgroundColor = message.lightBackgroundColor,
        darkBackgroundColor = message.darkBackgroundColor,
        textAlpha = message.textAlpha,
        modifier = modifier,
    )
}

@Immutable
private data class StyledRange(
    val start: Int,
    val length: Int,
    val color: Color,
    val userAnnotation: String? = null,
)

/**
 * Renders a moderation message (timeouts, bans, deletions) with colored usernames.
 */
@Composable
fun ModerationMessageComposable(
    message: ChatMessageUiState.ModerationMessageUi,
    fontSize: Float,
    onUserClick: (userId: String?, userName: String, displayName: String, channel: String?, badges: List<BadgeUi>, isLongPress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    animateGifs: Boolean = true,
    showChannelPrefix: Boolean = false,
) {
    val context = LocalPlatformContext.current
    val bgColor = rememberBackgroundColor(message.lightBackgroundColor, message.darkBackgroundColor)
    val textColor = rememberAdaptiveTextColor(bgColor)
    val timestampColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val creatorColor = rememberNormalizedColor(message.creatorColor, bgColor)
    val targetColor = rememberNormalizedColor(message.targetColor, bgColor)
    val textSize = fontSize.sp
    val resources = LocalResources.current
    val resolved = remember(message, resources) { message.resolveWithSpans(resources) }
    val resolvedMessage = resolved.text

    val linkColor = rememberAdaptiveLinkColor(bgColor)

    val dimmedTextColor = textColor.copy(alpha = 0.7f)

    val annotatedString =
        remember(
            message,
            resolved,
            textColor,
            dimmedTextColor,
            creatorColor,
            targetColor,
            linkColor,
            timestampColor,
            textSize,
        ) {
            // Usernames are colored and clickable, arguments use the regular text color
            val ranges =
                resolved.spans.map { span ->
                    when (span.role) {
                        ModerationSpan.Role.Creator -> {
                            val annotation = message.creatorUserName?.let { login -> "|${login.value}|${message.creatorName}|${message.channel.value}" }
                            StyledRange(span.start, span.length, creatorColor, annotation)
                        }

                        ModerationSpan.Role.Target -> {
                            val annotation = message.targetUserName?.let { login -> "|${login.value}|${message.targetName}|${message.channel.value}" }
                            StyledRange(span.start, span.length, targetColor, annotation)
                        }

                        ModerationSpan.Role.Argument -> {
                            StyledRange(span.start, span.length, textColor)
                        }
                    }
                }

            buildAnnotatedString {
                // Channel prefix
                if (showChannelPrefix) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append("#${message.channel.value} ")
                    }
                }

                // Timestamp
                if (message.timestamp.isNotEmpty()) {
                    withStyle(timestampSpanStyle(textSize.value, timestampColor)) {
                        append(message.timestamp)
                    }
                    appendInlineSpacer(6.dp)
                }

                // Render message: highlighted ranges at full opacity, template text dimmed
                var cursor = 0
                for (range in ranges) {
                    if (range.start > cursor) {
                        withStyle(SpanStyle(color = dimmedTextColor)) {
                            append(resolvedMessage.substring(cursor, range.start))
                        }
                    }
                    withStyle(SpanStyle(color = range.color)) {
                        val text = resolvedMessage.substring(range.start, range.start + range.length)
                        when (val annotation = range.userAnnotation) {
                            null -> append(text)

                            else -> {
                                pushStringAnnotation(tag = "USER", annotation = annotation)
                                append(text)
                                pop()
                            }
                        }
                    }
                    cursor = range.start + range.length
                }
                if (cursor < resolvedMessage.length) {
                    withStyle(SpanStyle(color = dimmedTextColor)) {
                        append(resolvedMessage.substring(cursor))
                    }
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .alpha(message.textAlpha)
                .background(bgColor)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        MessageTextWithInlineContent(
            annotatedString = annotatedString,
            badges = persistentListOf(),
            emotes = persistentListOf(),
            fontSize = fontSize,
            animateGifs = animateGifs,
            onEmoteClick = {},
            onTextClick = { offset ->
                val user = annotatedString.getStringAnnotations("USER", offset, offset).firstOrNull()
                val url = annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()

                when {
                    user != null -> parseUserAnnotation(user.item)?.let {
                        onUserClick(it.userId, it.userName, it.displayName, it.channel.orEmpty(), emptyList(), false)
                    }

                    url != null -> launchCustomTab(context, url.item)
                }
            },
            onTextLongClick = { offset ->
                val user = annotatedString.getStringAnnotations("USER", offset, offset).firstOrNull()
                user?.let { parseUserAnnotation(it.item) }?.let {
                    onUserClick(it.userId, it.userName, it.displayName, it.channel.orEmpty(), emptyList(), true)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
