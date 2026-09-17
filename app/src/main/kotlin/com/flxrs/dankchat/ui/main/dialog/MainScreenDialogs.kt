package com.flxrs.dankchat.ui.main.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.auth.StartupValidation
import com.flxrs.dankchat.data.auth.StartupValidationHolder
import com.flxrs.dankchat.data.repo.crash.CrashEntry
import com.flxrs.dankchat.data.repo.crash.CrashRepository
import com.flxrs.dankchat.data.repo.log.LogRepository
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.flxrs.dankchat.ui.main.channel.ChannelManagementViewModel
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.sheet.DebugInfoSheet
import com.flxrs.dankchat.ui.main.sheet.DebugInfoViewModel
import com.flxrs.dankchat.ui.main.sheet.InputSheetState
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import com.flxrs.dankchat.utils.compose.ConfirmationBottomSheet
import com.flxrs.dankchat.utils.compose.InfoBottomSheet
import com.flxrs.dankchat.utils.compose.InputBottomSheet
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenDialogs(
    dialogViewModel: DialogStateViewModel,
    isLoggedIn: Boolean,
    activeChannel: UserName?,
    isStreamActive: Boolean,
    inputSheetState: InputSheetState,
    sheetsReady: Boolean,
    onAddChannels: (List<UserName>) -> Unit,
    onLogout: () -> Unit,
    onLogin: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onJumpToMessage: (messageId: String, channel: UserName) -> Unit = { _, _ -> },
    onOpenLogViewer: () -> Unit = {},
) {
    val dialogState by dialogViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val channelManagementViewModel: ChannelManagementViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()
    val startupValidationHolder: StartupValidationHolder = koinInject()
    val startupValidation by startupValidationHolder.state.collectAsStateWithLifecycle()

    if (dialogState.showAddChannel) {
        AddChannelDialog(
            onDismiss = dialogViewModel::dismissAddChannel,
            onAddChannels = onAddChannels,
            isChannelAlreadyAdded = channelManagementViewModel::isChannelAdded,
        )
    }

    if (dialogState.showManageChannels) {
        val channels by channelManagementViewModel.channels.collectAsStateWithLifecycle()
        ManageChannelsDialog(
            channels = channels,
            onApplyChanges = channelManagementViewModel::applyChanges,
            onChannelSelect = channelManagementViewModel::selectChannel,
            onDismiss = dialogViewModel::dismissManageChannels,
        )
    }

    if (sheetsReady) {
        ModActionsSheetContainer(isStreamActive = isStreamActive)
    }

    if (dialogState.showRemoveChannel && activeChannel != null) {
        ConfirmationDialog(
            title = stringResource(R.string.confirm_channel_removal_message_named, activeChannel),
            confirmText = stringResource(R.string.confirm_channel_removal_positive_button),
            confirmColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            onConfirm = {
                channelManagementViewModel.removeChannel(activeChannel)
                dialogViewModel.dismissRemoveChannel()
            },
            onDismiss = dialogViewModel::dismissRemoveChannel,
        )
    }

    if (dialogState.showBlockChannel && activeChannel != null) {
        ConfirmationDialog(
            title = stringResource(R.string.confirm_channel_block_message_named, activeChannel),
            confirmText = stringResource(R.string.confirm_user_block_positive_button),
            confirmColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            onConfirm = {
                channelManagementViewModel.blockChannel(activeChannel)
                dialogViewModel.dismissBlockChannel()
            },
            onDismiss = dialogViewModel::dismissBlockChannel,
        )
    }

    if (dialogState.showLogout) {
        ConfirmationBottomSheet(
            title = stringResource(R.string.confirm_logout_message),
            confirmText = stringResource(R.string.confirm_logout_positive_button),
            confirmColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            onConfirm = {
                onLogout()
                dialogViewModel.dismissLogout()
            },
            onDismiss = dialogViewModel::dismissLogout,
        )
    }

    if (dialogState.pendingUploadAction != null) {
        UploadDisclaimerSheet(
            host = dialogViewModel.uploadHost,
            onConfirm = {
                dialogViewModel.acknowledgeExternalHosting()
                val action = dialogState.pendingUploadAction
                dialogViewModel.setPendingUploadAction(null)
                action?.invoke()
            },
            onDismiss = { dialogViewModel.setPendingUploadAction(null) },
        )
    }

    if (dialogState.showNewWhisper) {
        InputBottomSheet(
            title = stringResource(R.string.whisper_new_dialog_title),
            hint = stringResource(R.string.whisper_new_dialog_hint),
            confirmText = stringResource(R.string.whisper_new_dialog_start),
            showClearButton = true,
            onConfirm = { username ->
                chatInputViewModel.setWhisperTarget(UserName(username))
                dialogViewModel.dismissNewWhisper()
            },
            onDismiss = dialogViewModel::dismissNewWhisper,
        )
    }

    if (startupValidation is StartupValidation.ScopesOutdated) {
        InfoBottomSheet(
            title = stringResource(R.string.login_outdated_title),
            message = stringResource(R.string.login_outdated_message),
            confirmText = stringResource(R.string.oauth_expired_login_again),
            dismissible = false,
            onConfirm = onLogin,
            onDismiss = startupValidationHolder::acknowledge,
        )
    }

    if (startupValidation is StartupValidation.TokenInvalid) {
        InfoBottomSheet(
            title = stringResource(R.string.oauth_expired_title),
            message = stringResource(R.string.oauth_expired_message),
            confirmText = stringResource(R.string.oauth_expired_login_again),
            dismissText = stringResource(R.string.confirm_logout_positive_button),
            dismissible = false,
            onConfirm = onLogin,
            onDismiss = {
                startupValidationHolder.acknowledge()
                onLogout()
            },
        )
    }

    if (sheetsReady) {
        MessageOptionsSheetContainer(
            onJumpToMessage = onJumpToMessage,
        )
    }

    if (sheetsReady) {
        EmoteInfoSheetContainer(
            isLoggedIn = isLoggedIn,
            onOpenUrl = onOpenUrl,
        )
    }

    if (sheetsReady) {
        UserPopupSheetContainer(
            onOpenUrl = onOpenUrl,
        )
    }

    dialogState.crashEntry?.let { crashEntry ->
        val crashRepository: CrashRepository = koinInject()
        val logRepository: LogRepository = koinInject()
        val preferenceStore: DankChatPreferenceStore = koinInject()
        CrashReportDialog(
            crashEntry = crashEntry,
            userName = preferenceStore.userName?.value,
            userId = preferenceStore.userIdString?.value,
            onCopy = {
                val fullReport = crashRepository.buildEmailBody(crashEntry, preferenceStore.userName?.value, preferenceStore.userIdString?.value)
                val clipboardManager = context.getSystemService<ClipboardManager>()
                clipboardManager?.setPrimaryClip(ClipData.newPlainText("crash_report", fullReport))
            },
            onReport = {
                val reportMessage = dialogViewModel.getCrashReportMessage() ?: return@CrashReportDialog
                val channel = UserName(CRASH_REPORT_CHANNEL)
                if (!channelManagementViewModel.isChannelAdded(CRASH_REPORT_CHANNEL)) {
                    channelManagementViewModel.addChannel(channel)
                } else {
                    channelManagementViewModel.selectChannel(channel)
                }
                chatInputViewModel.insertText(reportMessage)
                dialogViewModel.dismissCrashReport()
            },
            onSendEmail = { includeLogs ->
                sendCrashEmail(
                    context = context,
                    crashEntry = crashEntry,
                    crashRepository = crashRepository,
                    logRepository = logRepository,
                    preferenceStore = preferenceStore,
                    includeLogs = includeLogs,
                )
                dialogViewModel.dismissCrashReport()
            },
            onDismiss = dialogViewModel::dismissCrashReport,
        )
    }

    if (sheetsReady && inputSheetState is InputSheetState.DebugInfo) {
        val debugInfoViewModel: DebugInfoViewModel = koinViewModel()
        DebugInfoSheet(
            viewModel = debugInfoViewModel,
            sheetState = rememberModalSheetState(),
            onDismiss = sheetNavigationViewModel::closeInputSheet,
            onOpenLogViewer = onOpenLogViewer,
        )
    }
}

@Composable
private fun UploadDisclaimerSheet(
    host: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    LocalUriHandler.current
    val disclaimerTemplate = stringResource(R.string.external_upload_disclaimer, host)
    val hostStart = disclaimerTemplate.indexOf(host)
    val annotatedText =
        buildAnnotatedString {
            append(disclaimerTemplate)
            if (hostStart >= 0) {
                addStyle(
                    style =
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    start = hostStart,
                    end = hostStart + host.length,
                )
                addLink(
                    url = LinkAnnotation.Url("https://$host"),
                    start = hostStart,
                    end = hostStart + host.length,
                )
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.nuuls_upload_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
            )

            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dialog_cancel))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dialog_ok))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashReportDialog(
    crashEntry: CrashEntry,
    userName: String?,
    userId: String?,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    onSendEmail: (includeLogs: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var showEmailConfirmation by remember { mutableStateOf(false) }
    var includeLogs by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(
            targetState = showEmailConfirmation,
            label = "CrashReportContent",
        ) { emailConfirmation ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
            ) {
                when {
                    emailConfirmation -> {
                        Text(
                            text = stringResource(R.string.crash_email_confirm_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                        )

                        Text(
                            text = stringResource(R.string.crash_email_confirm_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 12.dp),
                        )

                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Version: ${crashEntry.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Android: ${crashEntry.androidInfo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Device: ${crashEntry.device}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (userName != null) {
                                Text(
                                    text = "User: $userName (ID: ${userId.orEmpty()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = stringResource(R.string.crash_email_confirm_stacktrace),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clickable { includeLogs = !includeLogs }
                                .padding(start = 2.dp),
                        ) {
                            Checkbox(
                                checked = includeLogs,
                                onCheckedChange = null,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.crash_email_confirm_include_logs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, start = 8.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { showEmailConfirmation = false },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.dialog_cancel))
                            }
                            Button(
                                onClick = { onSendEmail(includeLogs) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.crash_report_email))
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = stringResource(R.string.crash_report_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                        )

                        Text(
                            text = stringResource(R.string.crash_report_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, start = 8.dp, end = 8.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = crashEntry.exceptionHeader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.crash_report_thread, crashEntry.threadName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(
                                    text = "v${crashEntry.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp, start = 8.dp, end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = onCopy,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.crash_report_copy))
                                }
                                Button(
                                    onClick = { showEmailConfirmation = true },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.crash_report_email))
                                }
                            }
                            OutlinedButton(
                                onClick = onReport,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.crash_report_send))
                            }
                            Text(
                                text = stringResource(R.string.crash_report_send_description),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sendCrashEmail(
    context: Context,
    crashEntry: CrashEntry,
    crashRepository: CrashRepository,
    logRepository: LogRepository,
    preferenceStore: DankChatPreferenceStore,
    includeLogs: Boolean,
) {
    val userName = preferenceStore.userName?.value
    val userId = preferenceStore.userIdString?.value
    val body = crashRepository.buildEmailBody(crashEntry, userName, userId)
    val exceptionType = crashEntry.exceptionHeader.substringBefore(':').substringAfterLast('.')
    val subject = "DankChat Crash Report [${userName.orEmpty()}] - $exceptionType"

    val emailIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(CRASH_REPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        if (includeLogs) {
            val logFile = logRepository.getLatestLogFile()
            if (logFile != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", logFile)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        selector = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
    }
    context.startActivity(Intent.createChooser(emailIntent, null))
}

private const val CRASH_REPORT_CHANNEL = "flex3rs"
private const val CRASH_REPORT_EMAIL = "dankchat@flxrs.com"
