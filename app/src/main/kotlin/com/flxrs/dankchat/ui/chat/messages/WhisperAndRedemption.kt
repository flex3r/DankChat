package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.ChatMessageUiState
import com.flxrs.dankchat.ui.chat.emote.EmoteSheetData
import com.flxrs.dankchat.ui.chat.messages.common.MessageTextWithInlineContent
import com.flxrs.dankchat.ui.chat.messages.common.appendInlineSpacer
import com.flxrs.dankchat.ui.chat.messages.common.appendWithLinks
import com.flxrs.dankchat.ui.chat.messages.common.isSpacerId
import com.flxrs.dankchat.ui.chat.messages.common.launchCustomTab
import com.flxrs.dankchat.ui.chat.messages.common.parseUserAnnotation
import com.flxrs.dankchat.ui.chat.messages.common.rememberAdaptiveLinkColor
import com.flxrs.dankchat.ui.chat.messages.common.rememberAdaptiveTextColor
import com.flxrs.dankchat.ui.chat.messages.common.rememberBackgroundColor
import com.flxrs.dankchat.ui.chat.messages.common.rememberNormalizedColor
import com.flxrs.dankchat.ui.chat.messages.common.spacerWidthDp
import com.flxrs.dankchat.ui.chat.messages.common.timestampSpanStyle

/**
 * Renders a whisper message (private message between users)
 */
@Composable
fun WhisperMessageComposable(
    message: ChatMessageUiState.WhisperMessageUi,
    fontSize: Float,
    onUserClick: (userId: String?, userName: String, displayName: String, badges: List<BadgeUi>, isLongPress: Boolean) -> Unit,
    onMessageLongClick: (messageId: String, fullMessage: String) -> Unit,
    onEmoteClick: (emotes: List<EmoteSheetData>) -> Unit,
    modifier: Modifier = Modifier,
    animateGifs: Boolean = true,
    onWhisperReply: ((userName: UserName) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor = rememberBackgroundColor(message.lightBackgroundColor, message.darkBackgroundColor)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .alpha(message.textAlpha)
                .background(backgroundColor)
                .indication(interactionSource, ripple())
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            WhisperMessageText(
                message = message,
                fontSize = fontSize,
                animateGifs = animateGifs,
                backgroundColor = backgroundColor,
                onUserClick = onUserClick,
                onMessageLongClick = onMessageLongClick,
                onEmoteClick = onEmoteClick,
            )
        }
        if (onWhisperReply != null) {
            IconButton(
                onClick = { onWhisperReply(message.replyTargetName) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WhisperMessageText(
    message: ChatMessageUiState.WhisperMessageUi,
    fontSize: Float,
    animateGifs: Boolean,
    backgroundColor: Color,
    onUserClick: (userId: String?, userName: String, displayName: String, badges: List<BadgeUi>, isLongPress: Boolean) -> Unit,
    onMessageLongClick: (messageId: String, fullMessage: String) -> Unit,
    onEmoteClick: (emotes: List<EmoteSheetData>) -> Unit,
) {
    val context = LocalPlatformContext.current
    val defaultTextColor = rememberAdaptiveTextColor(backgroundColor)
    val senderColor = rememberNormalizedColor(message.rawSenderColor, backgroundColor)
    val recipientColor = rememberNormalizedColor(message.rawRecipientColor, backgroundColor)
    val linkColor = rememberAdaptiveLinkColor(backgroundColor)

    // Build annotated string with text content
    val annotatedString =
        remember(message, defaultTextColor, senderColor, recipientColor, linkColor) {
            buildAnnotatedString {
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

                // Sender username with click annotation
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = senderColor,
                    ),
                ) {
                    pushStringAnnotation(
                        tag = SENDER_ANNOTATION_TAG,
                        annotation = "${message.userId.value}|${message.userName.value}|${message.displayName.value}",
                    )
                    append(message.senderName)
                    pop()
                }
                withStyle(SpanStyle(color = defaultTextColor)) {
                    append(" -> ")
                }

                // Recipient
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = recipientColor,
                    ),
                ) {
                    pushStringAnnotation(
                        tag = RECIPIENT_ANNOTATION_TAG,
                        annotation = "${message.recipientId?.value.orEmpty()}|${message.recipientUserName.value}|${message.recipientDisplayName.value}",
                    )
                    append(message.recipientName)
                    pop()
                }
                withStyle(SpanStyle(color = defaultTextColor)) {
                    append(": ")
                }

                // Message text with emotes
                withStyle(SpanStyle(color = defaultTextColor)) {
                    var currentPos = 0
                    message.emotes.sortedBy { it.position.first }.forEach { emote ->
                        // Text before emote
                        if (currentPos < emote.position.first) {
                            val segment = message.message.substring(currentPos, emote.position.first)
                            appendWithLinks(segment, currentPos, message.links, linkColor)
                        }

                        // Emote inline content
                        appendInlineContent("EMOTE_${emote.position}", emote.code)

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
        onEmoteClick = onEmoteClick,
        onTextClick = { offset ->
            val sender = annotatedString.getStringAnnotations(SENDER_ANNOTATION_TAG, offset, offset).firstOrNull()
            val recipient = annotatedString.getStringAnnotations(RECIPIENT_ANNOTATION_TAG, offset, offset).firstOrNull()
            val url = annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()

            when {
                sender != null -> parseUserAnnotation(sender.item)?.let {
                    onUserClick(it.userId, it.userName, it.displayName, message.badges, false)
                }

                recipient != null -> parseUserAnnotation(recipient.item)?.let {
                    onUserClick(it.userId, it.userName, it.displayName, emptyList(), false)
                }

                url != null -> launchCustomTab(context, url.item)
            }
        },
        onTextLongClick = { offset ->
            val sender = annotatedString.getStringAnnotations(SENDER_ANNOTATION_TAG, offset, offset).firstOrNull()
            val recipient = annotatedString.getStringAnnotations(RECIPIENT_ANNOTATION_TAG, offset, offset).firstOrNull()

            when {
                sender != null -> parseUserAnnotation(sender.item)?.let {
                    onUserClick(it.userId, it.userName, it.displayName, message.badges, true)
                }

                recipient != null -> parseUserAnnotation(recipient.item)?.let {
                    onUserClick(it.userId, it.userName, it.displayName, emptyList(), true)
                }

                else -> onMessageLongClick(message.id, message.fullMessage)
            }
        },
    )
}

/**
 * Renders a channel point redemption message
 */
@Composable
fun PointRedemptionMessageComposable(
    message: ChatMessageUiState.PointRedemptionMessageUi,
    fontSize: Float,
    modifier: Modifier = Modifier,
    highlightShape: Shape = RectangleShape,
) {
    val backgroundColor = rememberBackgroundColor(message.lightBackgroundColor, message.darkBackgroundColor)
    val textColor = rememberAdaptiveTextColor(backgroundColor)

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .alpha(message.textAlpha)
                .background(backgroundColor, highlightShape)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        val nameColor = message.nameText?.let { rememberNormalizedColor(message.rawNameColor, backgroundColor) }
        val rewardImageSize = (fontSize * 1.5f)
        val costTextStyle = TextStyle(fontSize = fontSize.sp, color = textColor)

        val annotatedString =
            remember(message, textColor, nameColor, fontSize) {
                buildAnnotatedString {
                    // Timestamp
                    if (message.timestamp.isNotEmpty()) {
                        withStyle(timestampSpanStyle(fontSize, textColor)) {
                            append(message.timestamp)
                        }
                        appendInlineSpacer(6.dp)
                    }

                    when {
                        message.requiresUserInput -> {
                            append("Redeemed ")
                        }

                        message.nameText != null -> {
                            withStyle(SpanStyle(color = nameColor ?: textColor)) {
                                append(message.nameText)
                            }
                            append(" redeemed ")
                        }
                    }

                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(message.title)
                    }
                    append(" ")
                    appendInlineContent(REWARD_COST_ID, "[reward ${message.cost}]")
                }
            }

        val inlineContent = remember(annotatedString, rewardImageSize, density, costTextStyle) {
            val costText = " ${message.cost}"
            val costWidthPx = textMeasurer.measure(costText, costTextStyle).size.width

            buildMap {
                val spacerIds = annotatedString
                    .getStringAnnotations(INLINE_CONTENT_TAG, 0, annotatedString.length)
                    .filter { isSpacerId(it.item) }
                    .map { it.item }
                    .distinct()

                for (id in spacerIds) {
                    val widthSp = with(density) { spacerWidthDp(id).dp.toSp() }
                    put(
                        id,
                        InlineTextContent(
                            Placeholder(width = widthSp, height = 0.01.sp, PlaceholderVerticalAlign.TextCenter),
                        ) { },
                    )
                }

                val imageSizeSp = with(density) { rewardImageSize.dp.toSp() }
                val costWidthSp = with(density) { costWidthPx.toDp().toSp() }
                val totalWidth = (imageSizeSp.value + costWidthSp.value).sp
                put(
                    REWARD_COST_ID,
                    InlineTextContent(
                        Placeholder(width = totalWidth, height = imageSizeSp, PlaceholderVerticalAlign.TextCenter),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = message.rewardImageUrl,
                                contentDescription = message.title,
                                modifier = Modifier.size(rewardImageSize.dp),
                            )
                            BasicText(
                                text = costText,
                                style = costTextStyle,
                            )
                        }
                    },
                )
            }
        }

        BasicText(
            text = annotatedString,
            style = costTextStyle,
            inlineContent = inlineContent,
        )
    }
}

private const val INLINE_CONTENT_TAG = "androidx.compose.foundation.text.inlineContent"
private const val REWARD_COST_ID = "REWARD_COST"
private const val SENDER_ANNOTATION_TAG = "WHISPER_SENDER"
private const val RECIPIENT_ANNOTATION_TAG = "WHISPER_RECIPIENT"
