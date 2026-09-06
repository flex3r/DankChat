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
) {
    val context = LocalPlatformContext.current
    val defaultTextColor = rememberAdaptiveTextColor(backgroundColor)
    val nameColor = rememberNormalizedColor(message.rawNameColor, backgroundColor)
    val linkColor = rememberAdaptiveLinkColor(backgroundColor)

    // Build annotated string with text content. Keyed on the content-affecting fields only,
    // so layout-only copies (rounded corners, divider) don't rebuild the string.
    val annotatedString =
        remember(
            message.id,
            message.timestamp,
            message.badges,
            message.nameText,
            message.message,
            message.emotes,
            message.isAction,
            defaultTextColor,
            nameColor,
            showChannelPrefix,
            linkColor,
            fontSize,
        ) {
            buildAnnotatedString {
                // Channel prefix (for mention tab)
                if (showChannelPrefix) {
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
                if (message.timestamp.isNotEmpty()) {
                    withStyle(timestampSpanStyle(fontSize, defaultTextColor)) {
                        append(message.timestamp)
                    }
                    appendInlineSpacer(6.dp)
                }

                // Badges (using appendInlineContent for proper rendering)
                message.badges.forEach { badge ->
                    appendInlineContent("BADGE_${badge.position}", "[badge]")
                    append(" ") // Space between badges
                }

                // Username with click annotation (only if nameText is not empty)
                if (message.nameText.isNotEmpty()) {
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
                    message.emotes.sortedBy { it.position.first }.forEach { emote ->
                        // Text before emote
                        if (currentPos < emote.position.first) {
                            val segment = message.message.substring(currentPos, emote.position.first)
                            appendWithLinks(segment, currentPos, message.links, linkColor)
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
                        if (nextPos < message.message.length && !message.message[nextPos].isWhitespace()) {
                            append(" ")
                        }

                        currentPos = emote.position.last + 1
                    }

                    // Remaining text
                    if (currentPos < message.message.length) {
                        val segment = message.message.substring(currentPos)
                        appendWithLinks(segment, currentPos, message.links, linkColor)
                    }
                }
            }
        }

    MessageTextWithInlineContent(
        annotatedString = annotatedString,
        badges = message.badges,
        emotes = message.emotes,
        fontSize = fontSize,
        animateGifs = animateGifs,
        isAsciiArt = message.isAsciiArt,
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
