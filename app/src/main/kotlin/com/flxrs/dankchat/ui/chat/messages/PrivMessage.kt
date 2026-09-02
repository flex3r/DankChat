package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.ChatMessageUiState
import com.flxrs.dankchat.ui.chat.TwitchGifContentPartUi
import com.flxrs.dankchat.ui.chat.emote.EmoteSheetData
import com.flxrs.dankchat.ui.chat.messages.common.MessageTextWithInlineContent
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
 * Renders a regular chat message with:
 * - Optional reply thread header
 * - Badges and username
 * - Message text with inline emotes
 * - Clickable username and emotes
 * - Long-press to copy message
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LambdaParameterEventTrailing")
@Composable
fun PrivMessageComposable(
    message: ChatMessageUiState.PrivMessageUi,
    fontSize: Float,
    onUserClick: (userId: String?, userName: String, displayName: String, channel: String?, badges: List<BadgeUi>, isLongPress: Boolean) -> Unit,
    onMessageLongClick: (messageId: String, channel: String?, fullMessage: String) -> Unit,
    onEmoteClick: (emotes: List<EmoteSheetData>) -> Unit,
    onReplyClick: (rootMessageId: String, replyName: UserName) -> Unit,
    modifier: Modifier = Modifier,
    highlightShape: Shape = RectangleShape,
    showChannelPrefix: Boolean = false,
    animateGifs: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = rememberBackgroundColor(message.lightBackgroundColor, message.darkBackgroundColor)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .alpha(message.textAlpha)
                .background(backgroundColor, highlightShape)
                .indication(interactionSource, ripple())
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        // Highlight type header (First Time Chat, Elevated Chat)
        if (message.highlightHeader != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerColor = rememberAdaptiveTextColor(backgroundColor).copy(alpha = 0.6f)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = headerColor,
                )
                Text(
                    text = message.highlightHeader.resolve(),
                    fontSize = (fontSize * 0.9f).sp,
                    fontWeight = FontWeight.Medium,
                    color = headerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .weight(1f, fill = false),
                )
                if (message.highlightHeaderImageUrl != null && message.highlightHeaderCost != null) {
                    AsyncImage(
                        model = message.highlightHeaderImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(14.dp),
                        alpha = 0.6f,
                    )
                    val costText = buildString {
                        append(message.highlightHeaderCost)
                        if (message.highlightHeaderCostSuffix != null) {
                            append(" ${message.highlightHeaderCostSuffix}")
                        }
                    }
                    Text(
                        text = costText,
                        fontSize = (fontSize * 0.9f).sp,
                        fontWeight = FontWeight.Medium,
                        color = headerColor,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }

        // Reply thread header
        if (message.thread != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onReplyClick(message.thread.rootId, message.thread.userName.toUserName()) },
                            onLongClick = { onMessageLongClick(message.id, message.channel.value, message.fullMessage) },
                        ).padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val replyColor = rememberAdaptiveTextColor(backgroundColor).copy(alpha = 0.6f)
                val replyNameColor = rememberNormalizedColor(message.thread.rawNameColor, backgroundColor)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = replyColor,
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = replyColor)) {
                            append("Reply to ")
                        }
                        withStyle(SpanStyle(color = replyNameColor)) {
                            append("@${message.thread.userName}: ")
                        }
                        withStyle(SpanStyle(color = replyColor)) {
                            append(message.thread.message)
                        }
                    },
                    fontSize = (fontSize * 0.9f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Main message
        if (message.gifContentParts.isEmpty()) {
            PrivMessageText(
                message = message,
                fontSize = fontSize,
                showChannelPrefix = showChannelPrefix,
                animateGifs = animateGifs,
                interactionSource = interactionSource,
                backgroundColor = backgroundColor,
                onUserClick = onUserClick,
                onMessageLongClick = onMessageLongClick,
                onEmoteClick = onEmoteClick,
            )
        } else {
            PrivMessageWithTwitchGifs(
                message = message,
                fontSize = fontSize,
                showChannelPrefix = showChannelPrefix,
                animateGifs = animateGifs,
                interactionSource = interactionSource,
                backgroundColor = backgroundColor,
                onUserClick = onUserClick,
                onMessageLongClick = onMessageLongClick,
                onEmoteClick = onEmoteClick,
            )
        }
    }
}

@Composable
private fun PrivMessageWithTwitchGifs(
    message: ChatMessageUiState.PrivMessageUi,
    fontSize: Float,
    showChannelPrefix: Boolean,
    animateGifs: Boolean,
    interactionSource: MutableInteractionSource,
    backgroundColor: Color,
    onUserClick: (userId: String?, userName: String, displayName: String, channel: String?, badges: List<BadgeUi>, isLongPress: Boolean) -> Unit,
    onMessageLongClick: (messageId: String, channel: String?, fullMessage: String) -> Unit,
    onEmoteClick: (emotes: List<EmoteSheetData>) -> Unit,
) {
    val context = LocalPlatformContext.current
    val parts = message.gifContentParts
    val firstText = parts.firstOrNull() as? TwitchGifContentPartUi.Text
    val hasVisiblePrefix =
        showChannelPrefix ||
            message.timestamp.isNotEmpty() ||
            message.badges.isNotEmpty() ||
            message.nameText.isNotEmpty()
    val gifFallbackColor =
        if (message.isAction) {
            rememberNormalizedColor(message.rawNameColor, backgroundColor)
        } else {
            rememberAdaptiveTextColor(backgroundColor)
        }

    if (firstText != null || hasVisiblePrefix) {
        PrivMessageText(
            message = message,
            part = firstText ?: TwitchGifContentPartUi.Text("", persistentListOf(), persistentListOf()),
            includeMessagePrefix = true,
            fontSize = fontSize,
            showChannelPrefix = showChannelPrefix,
            animateGifs = animateGifs,
            interactionSource = interactionSource,
            backgroundColor = backgroundColor,
            onUserClick = onUserClick,
            onMessageLongClick = onMessageLongClick,
            onEmoteClick = onEmoteClick,
        )
    }

    parts.drop(if (firstText != null) 1 else 0).forEach { part ->
        when (part) {
            is TwitchGifContentPartUi.Gif -> {
                TwitchGifContent(
                    gif = part.gif,
                    fontSize = fontSize,
                    fallbackColor = gifFallbackColor,
                    animateGifs = animateGifs,
                    onClick = { launchCustomTab(context, part.gif.url) },
                    onLongClick = { onMessageLongClick(message.id, message.channel.value, message.fullMessage) },
                )
            }

            is TwitchGifContentPartUi.Text -> {
                PrivMessageText(
                    message = message,
                    part = part,
                    includeMessagePrefix = false,
                    fontSize = fontSize,
                    showChannelPrefix = false,
                    animateGifs = animateGifs,
                    interactionSource = interactionSource,
                    backgroundColor = backgroundColor,
                    onUserClick = onUserClick,
                    onMessageLongClick = onMessageLongClick,
                    onEmoteClick = onEmoteClick,
                )
            }
        }
    }
}

@Composable
private fun PrivMessageText(
    message: ChatMessageUiState.PrivMessageUi,
    fontSize: Float,
    showChannelPrefix: Boolean,
    animateGifs: Boolean,
    interactionSource: MutableInteractionSource,
    backgroundColor: Color,
    onUserClick: (userId: String?, userName: String, displayName: String, channel: String?, badges: List<BadgeUi>, isLongPress: Boolean) -> Unit,
    onMessageLongClick: (messageId: String, channel: String?, fullMessage: String) -> Unit,
    onEmoteClick: (emotes: List<EmoteSheetData>) -> Unit,
    part: TwitchGifContentPartUi.Text? = null,
    includeMessagePrefix: Boolean = true,
) {
    val context = LocalPlatformContext.current
    val defaultTextColor = rememberAdaptiveTextColor(backgroundColor)
    val nameColor = rememberNormalizedColor(message.rawNameColor, backgroundColor)
    val linkColor = rememberAdaptiveLinkColor(backgroundColor)
    val text = part?.text ?: message.message
    val links = part?.links ?: message.links
    val emotes = part?.emotes ?: message.emotes

    // Build annotated string with text content. Keyed on the content-affecting fields only,
    // so layout-only copies (rounded corners, divider) don't rebuild the string.
    val annotatedString =
        remember(
            message.id,
            message.timestamp,
            message.badges,
            message.nameText,
            text,
            links,
            emotes,
            message.isAction,
            includeMessagePrefix,
            defaultTextColor,
            nameColor,
            showChannelPrefix,
            linkColor,
            fontSize,
        ) {
            buildAnnotatedString {
                // Channel prefix (for mention tab)
                if (includeMessagePrefix && showChannelPrefix) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = defaultTextColor,
                        ),
                    ) {
                        append("#${message.channel.value} ")
                    }
                }

                // Timestamp
                if (includeMessagePrefix && message.timestamp.isNotEmpty()) {
                    withStyle(timestampSpanStyle(fontSize, defaultTextColor)) {
                        append(message.timestamp)
                    }
                    appendInlineSpacer(6.dp)
                }

                // Badges (using appendInlineContent for proper rendering)
                if (includeMessagePrefix) {
                    message.badges.forEach { badge ->
                        appendInlineContent("BADGE_${badge.position}", "[badge]")
                        append(" ") // Space between badges
                    }
                }

                // Username with click annotation (only if nameText is not empty)
                if (includeMessagePrefix && message.nameText.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = nameColor,
                        ),
                    ) {
                        pushStringAnnotation(
                            tag = "USER",
                            annotation = "${message.userId?.value.orEmpty()}|${message.userName.value}|${message.displayName.value}|${message.channel.value}",
                        )
                        append(message.nameText)
                        pop()
                    }
                }

                // Message text with emotes
                val textColor =
                    if (message.isAction) {
                        nameColor
                    } else {
                        defaultTextColor
                    }

                withStyle(SpanStyle(color = textColor)) {
                    var currentPos = 0
                    emotes.sortedBy { it.position.first }.forEach { emote ->
                        // Text before emote
                        if (currentPos < emote.position.first) {
                            val segment = text.substring(currentPos, emote.position.first)
                            appendWithLinks(segment, currentPos, links, linkColor)
                        }

                        // Emote inline content
                        appendInlineContent("EMOTE_${emote.position}", emote.code)

                        // Cheer amount text
                        if (emote.cheerAmount != null) {
                            withStyle(
                                SpanStyle(
                                    color = emote.cheerColor ?: textColor,
                                    fontWeight = FontWeight.Bold,
                                ),
                            ) {
                                append(emote.cheerAmount.toString())
                            }
                        }

                        // Add space after emote if next character exists and is not whitespace
                        val nextPos = emote.position.last + 1
                        if (nextPos < text.length && !text[nextPos].isWhitespace()) {
                            append(" ")
                        }

                        currentPos = emote.position.last + 1
                    }

                    // Remaining text
                    if (currentPos < text.length) {
                        val segment = text.substring(currentPos)
                        appendWithLinks(segment, currentPos, links, linkColor)
                    }
                }
            }
        }

    MessageTextWithInlineContent(
        annotatedString = annotatedString,
        badges = if (includeMessagePrefix) message.badges else persistentListOf(),
        emotes = emotes,
        fontSize = fontSize,
        animateGifs = animateGifs,
        interactionSource = interactionSource,
        onEmoteClick = onEmoteClick,
        onTextClick = { offset ->
            val user = annotatedString.getStringAnnotations("USER", offset, offset).firstOrNull()
            val url = annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()

            when {
                user != null -> parseUserAnnotation(user.item)?.let {
                    onUserClick(it.userId, it.userName, it.displayName, it.channel.orEmpty(), message.badges, false)
                }

                url != null -> launchCustomTab(context, url.item)
            }
        },
        onTextLongClick = { offset ->
            val user = annotatedString.getStringAnnotations("USER", offset, offset).firstOrNull()

            when {
                user != null -> parseUserAnnotation(user.item)?.let {
                    onUserClick(it.userId, it.userName, it.displayName, it.channel.orEmpty(), message.badges, true)
                }

                else -> onMessageLongClick(message.id, message.channel.value, message.fullMessage)
            }
        },
    )
}
