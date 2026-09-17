package com.flxrs.dankchat.ui.chat.user

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.messages.common.BadgeInlineContent
import com.flxrs.dankchat.utils.compose.SheetErrorContent
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPopupDialog(
    state: UserPopupState,
    badges: ImmutableList<BadgeUi>,
    onBlockUser: () -> Unit,
    onUnblockUser: () -> Unit,
    ignoresHighlights: Boolean,
    onIgnoreHighlightsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onReport: (String) -> Unit,
    onMention: ((String, String) -> Unit)? = null,
    onWhisper: ((String) -> Unit)? = null,
    isOwnUser: Boolean = false,
    onMessageHistory: ((String) -> Unit)? = null,
    onViewHistory: ((String) -> Unit)? = null,
) {
    var showBlockConfirmation by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(
            targetState = showBlockConfirmation,
            label = "UserPopupContent",
        ) { isBlockConfirmation ->
            when {
                isBlockConfirmation -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.confirm_user_block_message),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 16.dp),
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                        ) {
                            OutlinedButton(onClick = { showBlockConfirmation = false }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.dialog_cancel))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    onBlockUser()
                                    showBlockConfirmation = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(stringResource(R.string.confirm_user_block_positive_button))
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (state) {
                            is UserPopupState.Error -> {
                                SheetErrorContent()
                            }

                            else -> {
                                val userName = state.userName
                                val displayName = state.displayName
                                val isSuccess = state is UserPopupState.Success
                                val isLoggedIn = state !is UserPopupState.NotLoggedIn
                                val isBlocked = (state as? UserPopupState.Success)?.isBlocked == true

                                UserInfoSection(
                                    state = state,
                                    userName = userName,
                                    displayName = displayName,
                                    badges = badges,
                                    onOpenChannel = onOpenChannel,
                                )

                                if (onMention != null && isLoggedIn) {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                                        modifier =
                                            Modifier.clickable {
                                                onMention(userName.value, displayName.value)
                                                onDismiss()
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        Text(stringResource(R.string.user_popup_mention))
                                    }
                                }
                                if (onWhisper != null && isLoggedIn && !isOwnUser) {
                                    ListItem(
                                        leadingContent = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                                        modifier =
                                            Modifier.clickable {
                                                onWhisper(userName.value)
                                                onDismiss()
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        Text(stringResource(R.string.user_popup_whisper))
                                    }
                                }
                                val historyCallback = onViewHistory ?: onMessageHistory
                                if (historyCallback != null) {
                                    val label = when (onViewHistory) {
                                        null -> R.string.message_history
                                        else -> R.string.view_history
                                    }
                                    ListItem(
                                        leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                                        modifier =
                                            Modifier.clickable {
                                                historyCallback(userName.value)
                                                onDismiss()
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        Text(stringResource(label))
                                    }
                                }
                                AnimatedVisibility(visible = isSuccess && !isOwnUser) {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Default.Block, contentDescription = null) },
                                        modifier =
                                            Modifier.clickable {
                                                if (isBlocked) {
                                                    onUnblockUser()
                                                } else {
                                                    showBlockConfirmation = true
                                                }
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        Text(if (isBlocked) stringResource(R.string.user_popup_unblock) else stringResource(R.string.user_popup_block))
                                    }
                                }
                                if (!isOwnUser) {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                                        trailingContent = {
                                            Switch(
                                                checked = ignoresHighlights,
                                                onCheckedChange = onIgnoreHighlightsChange,
                                            )
                                        },
                                        modifier = Modifier.clickable { onIgnoreHighlightsChange(!ignoresHighlights) },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        Text(stringResource(R.string.user_popup_ignore_highlights))
                                    }
                                }
                                if (!isOwnUser) {
                                    ListItem(
                                        leadingContent = { Icon(Icons.Default.Report, contentDescription = null) },
                                        modifier =
                                            Modifier.clickable {
                                                onReport(userName.value)
                                                onDismiss()
                                            },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    ) {
                                        Text(stringResource(R.string.user_popup_report))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserInfoSection(
    state: UserPopupState,
    userName: UserName,
    displayName: DisplayName,
    badges: ImmutableList<BadgeUi>,
    onOpenChannel: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Crossfade(targetState = state, label = "Avatar") { targetState ->
            when (targetState) {
                is UserPopupState.Success -> {
                    AsyncImage(
                        model = targetState.avatarUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .clickable { onOpenChannel(targetState.userName.value) },
                    )
                }

                is UserPopupState.Loading -> {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is UserPopupState.NotLoggedIn -> {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { onOpenChannel(targetState.userName.value) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is UserPopupState.Error -> {
                    Spacer(modifier = Modifier.size(96.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                text = userName.formatWithDisplayName(displayName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            val isSuccess = state is UserPopupState.Success
            AnimatedVisibility(visible = isSuccess) {
                val successState = state as? UserPopupState.Success
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (successState != null) {
                        Text(
                            text = stringResource(R.string.user_popup_created, successState.created),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        if (successState.showFollowingSince) {
                            Text(
                                text =
                                    successState.followingSince?.let {
                                        stringResource(R.string.user_popup_following_since, it)
                                    } ?: stringResource(R.string.user_popup_not_following),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (badges.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                badges.forEach { badge ->
                                    val title = badge.badge.title
                                    if (title != null) {
                                        TooltipBox(
                                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                                            tooltip = { PlainTooltip { Text(title) } },
                                            state = rememberTooltipState(),
                                        ) {
                                            BadgeInlineContent(badge = badge, size = 32.dp)
                                        }
                                    } else {
                                        BadgeInlineContent(badge = badge, size = 32.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val UserPopupState.userName: UserName
    get() =
        when (this) {
            is UserPopupState.Loading -> userName
            is UserPopupState.NotLoggedIn -> userName
            is UserPopupState.Success -> userName
            is UserPopupState.Error -> UserName("")
        }

private val UserPopupState.displayName: DisplayName
    get() =
        when (this) {
            is UserPopupState.Loading -> displayName
            is UserPopupState.NotLoggedIn -> displayName
            is UserPopupState.Success -> displayName
            is UserPopupState.Error -> DisplayName("")
        }
