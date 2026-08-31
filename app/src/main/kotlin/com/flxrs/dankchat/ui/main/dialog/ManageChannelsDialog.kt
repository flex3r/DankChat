package com.flxrs.dankchat.ui.main.dialog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.model.ChannelWithRename
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManageChannelsDialog(
    channels: List<ChannelWithRename>,
    mutedNotificationChannels: Set<UserName>,
    onApplyChanges: (List<ChannelWithRename>) -> Unit,
    onChannelSelect: (UserName) -> Unit,
    onChannelNotificationsChange: (UserName, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var channelToDelete by remember { mutableStateOf<UserName?>(null) }
    var editingChannel by remember { mutableStateOf<UserName?>(null) }

    // Local state for smooth reordering and deferred updates
    val localChannels = remember { mutableStateListOf<ChannelWithRename>() }
    LaunchedEffect(channels) {
        if (localChannels.isEmpty() && channels.isNotEmpty()) {
            localChannels.addAll(channels)
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            if (from.index in localChannels.indices && to.index in localChannels.indices) {
                localChannels.apply {
                    add(to.index, removeAt(from.index))
                }
            }
        }

    ModalBottomSheet(
        onDismissRequest = {
            onApplyChanges(localChannels.toList())
            onDismiss()
        },
        sheetState = rememberModalSheetState(),
        contentWindowInsets = { WindowInsets.statusBars },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(
            targetState = channelToDelete,
            label = "ManageChannelsContent",
        ) { deleteTarget ->
            when (deleteTarget) {
                null -> {
                    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        state = lazyListState,
                        contentPadding = navBarPadding,
                    ) {
                        itemsIndexed(localChannels, key = { _, item -> item.channel.value }) { index, channelWithRename ->
                            ReorderableItem(reorderableState, key = channelWithRename.channel.value) { isDragging ->
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)

                                Surface(
                                    shadowElevation = elevation,
                                    color =
                                        when {
                                            isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                                            else -> Color.Transparent
                                        },
                                ) {
                                    Column {
                                        ChannelItem(
                                            channelWithRename = channelWithRename,
                                            isEditing = editingChannel == channelWithRename.channel,
                                            notificationsEnabled = channelWithRename.channel.lowercase() !in mutedNotificationChannels,
                                            modifier =
                                                Modifier.longPressDraggableHandle(
                                                    onDragStarted = { /* Optional haptic feedback here */ },
                                                    onDragStopped = { /* Optional haptic feedback here */ },
                                                ),
                                            onNavigate = {
                                                onApplyChanges(localChannels.toList())
                                                onChannelSelect(channelWithRename.channel)
                                                onDismiss()
                                            },
                                            onEdit = {
                                                editingChannel =
                                                    when (editingChannel) {
                                                        channelWithRename.channel -> null
                                                        else -> channelWithRename.channel
                                                    }
                                            },
                                            onRename = { newName ->
                                                val rename = newName?.ifBlank { null }?.let { UserName(it) }
                                                localChannels[index] = localChannels[index].copy(rename = rename)
                                                editingChannel = null
                                            },
                                            onDelete = { channelToDelete = channelWithRename.channel },
                                            onNotificationsChange = { enabled ->
                                                onChannelNotificationsChange(channelWithRename.channel, enabled)
                                            },
                                        )
                                        if (index < localChannels.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (localChannels.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.no_channels_added),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }

                else -> {
                    DeleteChannelConfirmation(
                        channelName = deleteTarget,
                        onConfirm = {
                            localChannels.removeIf { it.channel == deleteTarget }
                            channelToDelete = null
                        },
                        onBack = { channelToDelete = null },
                    )
                }
            }
        }
    }
}

@Suppress("LambdaParameterEventTrailing")
@Composable
private fun ChannelItem(
    channelWithRename: ChannelWithRename,
    isEditing: Boolean,
    notificationsEnabled: Boolean,
    onNavigate: () -> Unit,
    onEdit: () -> Unit,
    onRename: (String?) -> Unit,
    onDelete: () -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_drag_handle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )

            Text(
                text =
                    buildAnnotatedString {
                        append(channelWithRename.rename?.value ?: channelWithRename.channel.value)
                        if (channelWithRename.rename != null && channelWithRename.rename != channelWithRename.channel) {
                            append(" ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontStyle = FontStyle.Italic)) {
                                append(channelWithRename.channel.value)
                            }
                        }
                    },
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
            )

            IconButton(onClick = { onNotificationsChange(!notificationsEnabled) }) {
                Icon(
                    imageVector =
                        when {
                            notificationsEnabled -> Icons.Default.Notifications
                            else -> Icons.Default.NotificationsOff
                        },
                    contentDescription =
                        stringResource(
                            when {
                                notificationsEnabled -> R.string.disable_channel_notifications
                                else -> R.string.enable_channel_notifications
                            },
                            channelWithRename.channel.value,
                        ),
                )
            }

            IconButton(onClick = onNavigate) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = stringResource(R.string.open_channel),
                )
            }

            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.edit_dialog_title),
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_outline),
                    contentDescription = stringResource(R.string.remove_channel),
                )
            }
        }

        AnimatedVisibility(
            visible = isEditing,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
            modifier = Modifier.imePadding(),
        ) {
            InlineRenameField(
                channelWithRename = channelWithRename,
                onRename = onRename,
            )
        }
    }
}

@Composable
private fun InlineRenameField(
    channelWithRename: ChannelWithRename,
    onRename: (String?) -> Unit,
) {
    val initialText = channelWithRename.rename?.value.orEmpty()
    var renameText by remember(channelWithRename.channel) {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.edit_dialog_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                placeholder = { Text(channelWithRename.channel.value) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        onRename(renameText.text)
                    }),
                trailingIcon =
                    if (renameText.text.isNotEmpty()) {
                        {
                            IconButton(onClick = { onRename(null) }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_clear),
                                    contentDescription = stringResource(R.string.clear),
                                )
                            }
                        }
                    } else {
                        null
                    },
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
            )

            TextButton(
                onClick = { onRename(renameText.text) },
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun DeleteChannelConfirmation(
    channelName: UserName,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.confirm_channel_removal_message_named, channelName.value),
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
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.dialog_cancel))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.confirm_channel_removal_positive_button))
            }
        }
    }
}
