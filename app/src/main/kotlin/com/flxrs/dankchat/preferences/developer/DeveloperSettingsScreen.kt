package com.flxrs.dankchat.preferences.developer

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.repo.crash.CrashRepository
import com.flxrs.dankchat.data.repo.log.LogRepository
import com.flxrs.dankchat.preferences.components.ExpandablePreferenceItem
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceCategory
import com.flxrs.dankchat.preferences.components.PreferenceItem
import com.flxrs.dankchat.preferences.components.SwitchPreferenceItem
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsInteraction.EventSubDebugOutput
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsInteraction.EventSubEnabled
import com.flxrs.dankchat.preferences.developer.customlogin.CustomLoginState
import com.flxrs.dankchat.preferences.developer.customlogin.CustomLoginViewModel
import com.flxrs.dankchat.utils.compose.ConfirmationBottomSheet
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import com.flxrs.dankchat.utils.extensions.truncate
import com.jakewharton.processphoenix.ProcessPhoenix
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DeveloperSettingsScreen(
    onBack: () -> Unit,
    onOpenLogViewer: (fileName: String) -> Unit = {},
    onOpenCrashViewer: (crashId: Long) -> Unit = {},
) {
    val viewModel = koinViewModel<DeveloperSettingsViewModel>()
    val settings = viewModel.settings.collectAsStateWithLifecycle().value

    val context = LocalContext.current
    val restartRequiredTitle = stringResource(R.string.restart_required)
    val restartRequiredAction = stringResource(R.string.restart)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            when (it) {
                DeveloperSettingsEvent.RestartRequired -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = restartRequiredTitle,
                            actionLabel = restartRequiredAction,
                            duration = SnackbarDuration.Short,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        ProcessPhoenix.triggerRebirth(context)
                    }
                }

                DeveloperSettingsEvent.ImmediateRestart -> {
                    ProcessPhoenix.triggerRebirth(context)
                }
            }
        }
    }

    DeveloperSettingsContent(
        settings = settings,
        snackbarHostState = snackbarHostState,
        onInteraction = { viewModel.onInteraction(it) },
        onBack = onBack,
        onOpenLogViewer = onOpenLogViewer,
        onOpenCrashViewer = onOpenCrashViewer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperSettingsContent(
    settings: DeveloperSettings,
    snackbarHostState: SnackbarHostState,
    onInteraction: (DeveloperSettingsInteraction) -> Unit,
    onBack: () -> Unit,
    onOpenLogViewer: (fileName: String) -> Unit,
    onOpenCrashViewer: (crashId: Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_developer_header)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
        ) {
            PreferenceCategory(title = stringResource(R.string.preference_developer_category_general)) {
                run {
                    val logRepository: LogRepository = koinInject()
                    val logFiles = remember { logRepository.getLogFiles() }
                    ExpandablePreferenceItem(
                        title = stringResource(R.string.preference_log_viewer_title),
                        summary = stringResource(R.string.preference_log_viewer_summary),
                    ) {
                        val scope = rememberCoroutineScope()
                        val sheetState = rememberModalSheetState()
                        ModalBottomSheet(
                            onDismissRequest = ::dismiss,
                            sheetState = sheetState,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            if (logFiles.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.preference_log_viewer_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(16.dp),
                                )
                            } else {
                                logFiles.forEach { file ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    scope.launch {
                                                        sheetState.hide()
                                                        dismiss()
                                                    }
                                                    onOpenLogViewer(file.name)
                                                }.padding(horizontal = 16.dp, vertical = 14.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
                SwitchPreferenceItem(
                    title = stringResource(R.string.preference_debug_mode_title),
                    summary = stringResource(R.string.preference_debug_mode_summary),
                    isChecked = settings.debugMode,
                    onClick = { onInteraction(DeveloperSettingsInteraction.DebugMode(it)) },
                )
                val crashRepository: CrashRepository = koinInject()
                var crashes by remember { mutableStateOf(crashRepository.getRecentCrashes()) }
                ExpandablePreferenceItem(
                    title = stringResource(R.string.preference_crash_viewer_title),
                    summary = stringResource(R.string.preference_crash_viewer_summary),
                    isEnabled = settings.debugMode,
                ) {
                    val scope = rememberCoroutineScope()
                    val sheetState = rememberModalSheetState()
                    var confirmationState by remember { mutableStateOf<CrashDeleteConfirmation>(CrashDeleteConfirmation.None) }
                    ModalBottomSheet(
                        onDismissRequest = ::dismiss,
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        AnimatedContent(
                            targetState = confirmationState,
                            label = "CrashDeleteConfirmation",
                        ) { state ->
                            when (state) {
                                is CrashDeleteConfirmation.None -> {
                                    if (crashes.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.preference_crash_viewer_empty),
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(16.dp),
                                        )
                                    } else {
                                        Column {
                                            TextButton(
                                                onClick = { confirmationState = CrashDeleteConfirmation.All },
                                                modifier = Modifier
                                                    .align(Alignment.End)
                                                    .padding(end = 8.dp),
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.crash_viewer_clear_all),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                            crashes.forEach { crash ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                scope.launch {
                                                                    sheetState.hide()
                                                                    dismiss()
                                                                }
                                                                onOpenCrashViewer(crash.id)
                                                            }.padding(horizontal = 16.dp, vertical = 14.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.BugReport,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                    Column {
                                                        Text(
                                                            text = crash.exceptionHeader,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            text = crash.timestamp,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                is CrashDeleteConfirmation.All -> {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.crash_viewer_clear_all_confirm),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            OutlinedButton(
                                                onClick = { confirmationState = CrashDeleteConfirmation.None },
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(stringResource(R.string.dialog_cancel))
                                            }
                                            Button(
                                                onClick = {
                                                    crashRepository.deleteAllCrashes()
                                                    crashes = emptyList()
                                                    confirmationState = CrashDeleteConfirmation.None
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error,
                                                ),
                                            ) {
                                                Text(stringResource(R.string.crash_viewer_clear_all))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
                SwitchPreferenceItem(
                    title = stringResource(R.string.preference_repeated_sending_title),
                    summary = stringResource(R.string.preference_repeated_sending_summary),
                    isChecked = settings.repeatedSending,
                    onClick = { onInteraction(DeveloperSettingsInteraction.RepeatedSending(it)) },
                )
                ExpandablePreferenceItem(title = stringResource(R.string.preference_rm_host_title)) {
                    CustomRecentMessagesHostBottomSheet(
                        initialHost = settings.customRecentMessagesHost,
                        onInteraction = {
                            dismiss()
                            onInteraction(it)
                        },
                    )
                }
            }

            PreferenceCategory(title = stringResource(R.string.preference_developer_category_twitch)) {
                SwitchPreferenceItem(
                    title = stringResource(R.string.preference_bypass_command_handling_title),
                    summary = stringResource(R.string.preference_bypass_command_handling_summary),
                    isChecked = settings.bypassCommandHandling,
                    onClick = { onInteraction(DeveloperSettingsInteraction.BypassCommandHandling(it)) },
                )
                SwitchPreferenceItem(
                    title = stringResource(R.string.preference_helix_sending_title),
                    summary = stringResource(R.string.preference_helix_sending_summary),
                    isChecked = settings.chatSendProtocol == ChatSendProtocol.Helix,
                    onClick = { enabled ->
                        val protocol =
                            when {
                                enabled -> ChatSendProtocol.Helix
                                else -> ChatSendProtocol.IRC
                            }
                        onInteraction(DeveloperSettingsInteraction.ChatSendProtocolChanged(protocol))
                    },
                )
                if (!settings.isPubSubShutdown) {
                    SwitchPreferenceItem(
                        title = stringResource(R.string.preference_eventsub_title),
                        summary = stringResource(R.string.preference_eventsub_summary),
                        isChecked = settings.shouldUseEventSub,
                        onClick = { onInteraction(EventSubEnabled(it)) },
                    )
                }
                SwitchPreferenceItem(
                    title = stringResource(R.string.preference_eventsub_debug_title),
                    summary = stringResource(R.string.preference_eventsub_debug_summary),
                    isEnabled = settings.shouldUseEventSub,
                    isChecked = settings.eventSubDebugOutput,
                    onClick = { onInteraction(EventSubDebugOutput(it)) },
                )
            }

            PreferenceCategory(title = stringResource(R.string.preference_developer_category_auth)) {
                ExpandablePreferenceItem(title = stringResource(R.string.preference_custom_login_title)) {
                    CustomLoginBottomSheet(
                        onDismissRequest = ::dismiss,
                        onRequestRestart = {
                            dismiss()
                            onInteraction(DeveloperSettingsInteraction.RestartRequired)
                        },
                    )
                }
                PreferenceItem(
                    title = stringResource(R.string.preference_revoke_token_title),
                    summary = stringResource(R.string.preference_revoke_token_summary),
                    onClick = { onInteraction(DeveloperSettingsInteraction.RevokeToken) },
                )
            }

            PreferenceCategory(title = stringResource(R.string.preference_reset_onboarding_category)) {
                PreferenceItem(
                    title = stringResource(R.string.preference_reset_onboarding_title),
                    summary = stringResource(R.string.preference_reset_onboarding_summary),
                    onClick = { onInteraction(DeveloperSettingsInteraction.ResetOnboarding) },
                )
                PreferenceItem(
                    title = stringResource(R.string.preference_reset_tour_title),
                    summary = stringResource(R.string.preference_reset_tour_summary),
                    onClick = { onInteraction(DeveloperSettingsInteraction.ResetTour) },
                )
            }

            NavigationBarSpacer()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRecentMessagesHostBottomSheet(
    initialHost: String,
    onInteraction: (DeveloperSettingsInteraction) -> Unit,
) {
    var host by remember(initialHost) { mutableStateOf(initialHost) }
    ModalBottomSheet(
        onDismissRequest = { onInteraction(DeveloperSettingsInteraction.CustomRecentMessagesHost(host)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.preference_rm_host_title),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
        TextButton(
            onClick = { host = DeveloperSettings.RM_HOST_DEFAULT },
            content = { Text(stringResource(R.string.reset)) },
            modifier =
                Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            value = host,
            onValueChange = { host = it },
            label = { Text(stringResource(R.string.host)) },
            maxLines = 1,
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                ),
        )
        Spacer(Modifier.height(64.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomLoginBottomSheet(
    onDismissRequest: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val customLoginViewModel = koinInject<CustomLoginViewModel>()
    val state = customLoginViewModel.customLoginState.collectAsStateWithLifecycle().value
    val token = rememberTextFieldState(customLoginViewModel.getToken())
    var showScopesDialog by remember { mutableStateOf(false) }

    val error =
        when (state) {
            is CustomLoginState.Failure -> stringResource(R.string.custom_login_error_fallback, state.error.truncate())
            CustomLoginState.TokenEmpty -> stringResource(R.string.custom_login_error_empty_token)
            CustomLoginState.TokenInvalid -> stringResource(R.string.custom_login_error_invalid_token)
            is CustomLoginState.MissingScopes -> stringResource(R.string.custom_login_error_missing_scopes, state.missingScopes.truncate())
            else -> null
        }

    SideEffect(state) {
        if (state is CustomLoginState.Validated) {
            onRequestRestart()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.preference_custom_login_title),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
            )
            Text(
                text = stringResource(R.string.custom_login_hint),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.End),
            ) {
                TextButton(
                    onClick = { showScopesDialog = true },
                    content = { Text(stringResource(R.string.custom_login_show_scopes)) },
                )
                TextButton(
                    onClick = { token.setTextAndPlaceCursorAtEnd(customLoginViewModel.getToken()) },
                    content = { Text(stringResource(R.string.reset)) },
                )
            }

            var showPassword by remember { mutableStateOf(false) }
            androidx.compose.material3.OutlinedSecureTextField(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                state = token,
                textObfuscationMode =
                    when {
                        showPassword -> TextObfuscationMode.Visible
                        else -> TextObfuscationMode.Hidden
                    },
                label = { Text(stringResource(R.string.oauth_token)) },
                isError = error != null,
                supportingText = { error?.let { Text(it) } },
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        content = {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                            )
                        },
                    )
                },
                keyboardOptions =
                    KeyboardOptions(
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                    ),
            )

            AnimatedVisibility(visible = state !is CustomLoginState.Loading, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        scope.launch { customLoginViewModel.validateCustomLogin(token.text.toString()) }
                    },
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                    content = {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.verify_login))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.verify_login))
                        }
                    },
                )
            }
            AnimatedVisibility(visible = state is CustomLoginState.Loading, modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator()
            }
            Spacer(Modifier.height(64.dp))
        }
    }

    if (showScopesDialog) {
        ShowScopesBottomSheet(
            scopes = customLoginViewModel.getScopes(),
            onDismissRequest = { showScopesDialog = false },
        )
    }

    if (state is CustomLoginState.MissingScopes && state.dialogOpen) {
        MissingScopesDialog(
            missing = state.missingScopes,
            onDismissRequest = { customLoginViewModel.dismissMissingScopesDialog() },
            onContinue = {
                customLoginViewModel.saveLogin(state.token, state.validation)
                onRequestRestart()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowScopesBottomSheet(
    scopes: String,
    onDismissRequest: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.custom_login_required_scopes),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = scopes,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("scopes", scopes)))
                            }
                        },
                        content = { Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null) },
                    )
                },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MissingScopesDialog(
    missing: String,
    onDismissRequest: () -> Unit,
    onContinue: () -> Unit,
) {
    ConfirmationBottomSheet(
        title = stringResource(R.string.custom_login_missing_scopes_title),
        message = stringResource(R.string.custom_login_missing_scopes_text, missing),
        confirmText = stringResource(R.string.custom_login_missing_scopes_continue),
        onConfirm = onContinue,
        onDismiss = onDismissRequest,
    )
}

private sealed interface CrashDeleteConfirmation {
    data object None : CrashDeleteConfirmation

    data object All : CrashDeleteConfirmation
}
