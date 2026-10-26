package com.flxrs.dankchat.ui.chat.messages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.ChatMessageUiState
import com.flxrs.dankchat.ui.chat.ChatMessageUiState.AutomodMessageUi.AutomodMessageStatus
import com.flxrs.dankchat.ui.chat.emote.emoteBaseHeight
import com.flxrs.dankchat.ui.chat.messages.common.EmoteDimensions
import com.flxrs.dankchat.ui.chat.messages.common.TextWithMeasuredInlineContent
import com.flxrs.dankchat.ui.chat.messages.common.appendInlineSpacer
import com.flxrs.dankchat.ui.chat.messages.common.badgeInlineId
import com.flxrs.dankchat.ui.chat.messages.common.rememberInlineImages
import com.flxrs.dankchat.ui.chat.messages.common.rememberNormalizedColor
import com.flxrs.dankchat.utils.resolve
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap

private val AutoModBlue = Color(0xFF448AFF)

private const val ALLOW_TAG = "ALLOW"
private const val DENY_TAG = "DENY"
private const val BAN_TAG = "BAN"

@Composable
fun AutomodMessageComposable(
    message: ChatMessageUiState.AutomodMessageUi,
    fontSize: Float,
    onAllow: (heldMessageId: String, channel: UserName) -> Unit,
    onDeny: (heldMessageId: String, channel: UserName) -> Unit,
    onMessageLongClick: () -> Unit,
    onBanUser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val timestampColor = MaterialTheme.colorScheme.onSurface
    val allowColor = MaterialTheme.colorScheme.primary
    val denyColor = MaterialTheme.colorScheme.error
    val textSize = fontSize.sp
    val isPending = message.status == AutomodMessageStatus.Pending
    val isCompleted = !message.isUserSide && message.status != AutomodMessageStatus.Pending
    val backgroundColor = MaterialTheme.colorScheme.background
    val nameColor = rememberNormalizedColor(message.rawNameColor, backgroundColor)

    // Faded colors for completed (approved/denied/expired) header — "Ban user" stays at full opacity
    val headerTextColor = if (isCompleted) textColor.copy(alpha = 0.5f) else textColor
    val headerTimestampColor = if (isCompleted) timestampColor.copy(alpha = 0.5f) else timestampColor

    // Resolve strings
    val headerText = stringResource(R.string.automod_header, message.reason.resolve())
    val allowText = stringResource(R.string.automod_allow)
    val denyText = stringResource(R.string.automod_deny)
    val approvedText = stringResource(R.string.automod_status_approved)
    val deniedText = stringResource(R.string.automod_status_denied)
    val banUserText = stringResource(R.string.automod_ban_user)
    val expiredText = stringResource(R.string.automod_status_expired)
    val userHeldText = stringResource(R.string.automod_user_held)
    val userAcceptedText = stringResource(R.string.automod_user_accepted)
    val userDeniedText = stringResource(R.string.automod_user_denied)

    // Header line: [badge] "AutoMod: ..."
    val headerString =
        remember(
            message,
            headerTextColor,
            headerTimestampColor,
            allowColor,
            denyColor,
            textSize,
            headerText,
            allowText,
            denyText,
            approvedText,
            deniedText,
            expiredText,
            userHeldText,
            userAcceptedText,
            userDeniedText,
            banUserText,
        ) {
            buildAnnotatedString {
                // Timestamp
                if (message.timestamp.isNotEmpty()) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = textSize * 0.95f,
                            color = headerTimestampColor,
                            letterSpacing = (-0.03).em,
                        ),
                    ) {
                        append(message.timestamp)
                    }
                    appendInlineSpacer(6.dp)
                }

                // Badges
                message.badges.forEach { badge ->
                    appendInlineContent("BADGE_${badge.position}", "[badge]")
                    append(" ")
                }

                // "AutoMod: " in blue bold
                val autoModBlue = if (isCompleted) AutoModBlue.copy(alpha = 0.5f) else AutoModBlue
                withStyle(SpanStyle(color = autoModBlue, fontWeight = FontWeight.Bold)) {
                    append("AutoMod: ")
                }

                when {
                    // User-side: simple status messages, no Allow/Deny
                    message.isUserSide -> {
                        when (message.status) {
                            AutomodMessageStatus.Pending -> withStyle(SpanStyle(color = headerTextColor)) { append(userHeldText) }
                            AutomodMessageStatus.Approved -> withStyle(SpanStyle(color = headerTextColor)) { append(userAcceptedText) }
                            AutomodMessageStatus.Denied -> withStyle(SpanStyle(color = headerTextColor)) { append(userDeniedText) }
                            AutomodMessageStatus.Expired -> withStyle(SpanStyle(color = textColor.copy(alpha = 0.5f))) { append(expiredText) }
                        }
                    }

                    // Mod-side: reason text + Allow/Deny buttons or status
                    else -> {
                        withStyle(SpanStyle(color = headerTextColor)) {
                            append("$headerText ")
                        }

                        when (message.status) {
                            AutomodMessageStatus.Pending -> {
                                pushStringAnnotation(tag = ALLOW_TAG, annotation = message.heldMessageId)
                                withStyle(SpanStyle(color = allowColor, fontWeight = FontWeight.Bold)) {
                                    append(allowText)
                                }
                                pop()

                                pushStringAnnotation(tag = DENY_TAG, annotation = message.heldMessageId)
                                withStyle(SpanStyle(color = denyColor, fontWeight = FontWeight.Bold)) {
                                    append("  $denyText")
                                }
                                pop()
                            }

                            AutomodMessageStatus.Approved -> {
                                withStyle(SpanStyle(color = allowColor.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)) {
                                    append(approvedText)
                                }
                            }

                            AutomodMessageStatus.Denied -> {
                                withStyle(SpanStyle(color = denyColor.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)) {
                                    append(deniedText)
                                }

                                pushStringAnnotation(tag = BAN_TAG, annotation = "ban")
                                withStyle(SpanStyle(color = denyColor, fontWeight = FontWeight.Bold)) {
                                    append("  $banUserText")
                                }
                                pop()
                            }

                            AutomodMessageStatus.Expired -> {
                                withStyle(SpanStyle(color = textColor.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)) {
                                    append(expiredText)
                                }
                            }
                        }
                    }
                }
            }
        }

    // Body line: "timestamp {displayName}: {message}"
    val bodyString =
        remember(message, textColor, nameColor, timestampColor, textSize) {
            message.messageText?.let { text ->
                buildAnnotatedString {
                    // Timestamp for alignment
                    if (message.timestamp.isNotEmpty()) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = textSize * 0.95f,
                                color = timestampColor,
                                letterSpacing = (-0.03).em,
                            ),
                        ) {
                            append(message.timestamp)
                        }
                        appendInlineSpacer(6.dp)
                    }

                    // Username in bold with user color
                    withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
                        append("${message.userDisplayName}: ")
                    }

                    // Message text
                    withStyle(SpanStyle(color = textColor)) {
                        append(text)
                    }
                }
            }
        }

    val badgeSize = emoteBaseHeight(fontSize)
    val inlineImages = rememberInlineImages(badges = message.badges, emotes = persistentListOf(), fontSize = fontSize)

    val density = LocalDensity.current
    val knownDimensions =
        remember(message.badges, fontSize) {
            buildMap {
                val badgeSizePx = with(density) { badgeSize.toPx().toInt() }
                message.badges.forEach { badge ->
                    val id = badgeInlineId(badge)
                    put(id, EmoteDimensions(id, badgeSizePx, badgeSizePx))
                }
            }.toImmutableMap()
        }

    val resolvedAlpha =
        when {
            message.isUserSide -> 1f
            message.status == AutomodMessageStatus.Pending -> 1f
            else -> 0.5f
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        // Header line with badge inline content (alpha handled via span colors, not modifier)
        TextWithMeasuredInlineContent(
            text = headerString,
            inlineImages = inlineImages,
            style = TextStyle(fontSize = textSize),
            knownDimensions = knownDimensions,
            modifier = Modifier.fillMaxWidth(),
            onTextClick = { offset ->
                if (isPending) {
                    headerString
                        .getStringAnnotations(ALLOW_TAG, offset, offset)
                        .firstOrNull()
                        ?.let {
                            onAllow(message.heldMessageId, message.channel)
                        }
                    headerString
                        .getStringAnnotations(DENY_TAG, offset, offset)
                        .firstOrNull()
                        ?.let {
                            onDeny(message.heldMessageId, message.channel)
                        }
                }
                headerString
                    .getStringAnnotations(BAN_TAG, offset, offset)
                    .firstOrNull()
                    ?.let {
                        onBanUser()
                    }
            },
            onTextLongClick = { onMessageLongClick() },
        )

        // Body line with held message text
        bodyString?.let {
            TextWithMeasuredInlineContent(
                text = it,
                style = TextStyle(fontSize = textSize),
                modifier = Modifier.fillMaxWidth().alpha(resolvedAlpha),
                onTextLongClick = { onMessageLongClick() },
            )
        }
    }
}
