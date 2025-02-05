package com.flxrs.dankchat.preferences.tools

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceCategory
import com.flxrs.dankchat.preferences.components.PreferenceItem
import com.flxrs.dankchat.preferences.components.PreferenceListDialog
import com.flxrs.dankchat.preferences.components.SwitchPreferenceItem
import com.flxrs.dankchat.preferences.tools.upload.RecentUpload
import com.flxrs.dankchat.preferences.tools.upload.RecentUploadsViewModel
import com.flxrs.dankchat.utils.compose.buildLinkAnnotation
import com.flxrs.dankchat.utils.compose.textLinkStyles
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.autolinktext.rememberAutoLinkText

@Composable
fun ToolsSettingsScreen(
    onNavToImageUploader: () -> Unit,
    onNavToTTSUserIgnoreList: () -> Unit,
    onNavBack: () -> Unit,
) {
    val viewModel = koinViewModel<ToolsSettingsViewModel>()
    val settings = viewModel.settings.collectAsStateWithLifecycle().value

    ToolsSettingsScreen(
        settings = settings,
        onInteraction = { viewModel.onInteraction(it) },
        onNavToImageUploader = onNavToImageUploader,
        onNavToTTSUserIgnoreList = onNavToTTSUserIgnoreList,
        onNavBack = onNavBack,
    )
}

@Composable
private fun ToolsSettingsScreen(
    settings: ToolsSettingsState,
    onInteraction: (ToolsSettingsInteraction) -> Unit,
    onNavToImageUploader: () -> Unit,
    onNavToTTSUserIgnoreList: () -> Unit,
    onNavBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_tools_header)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavBack,
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ImageUploaderCategory(hasRecentUploads = settings.hasRecentUploads, onNavToImageUploader = onNavToImageUploader)
            HorizontalDivider(thickness = Dp.Hairline)
            TextToSpeechCategory(settings, onInteraction, onNavToTTSUserIgnoreList)
            NavigationBarSpacer()
        }
    }
}

@Composable
fun ImageUploaderCategory(
    hasRecentUploads: Boolean,
    onNavToImageUploader: () -> Unit,
) {
    var recentUploadSheetOpen by remember { mutableStateOf(false) }
    PreferenceCategory(title = stringResource(R.string.preference_uploader_header)) {
        PreferenceItem(
            title = stringResource(R.string.preference_uploader_configure_title),
            onClick = onNavToImageUploader,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )
        PreferenceItem(
            title = stringResource(R.string.preference_uploader_recent_uploads_title),
            isEnabled = hasRecentUploads,
            onClick = { recentUploadSheetOpen = true },
            trailingIcon = Icons.Default.History,
        )
    }

    if (recentUploadSheetOpen) {
        var confirmClearDialog by remember { mutableStateOf(false) }
        val viewModel = koinViewModel<RecentUploadsViewModel>()
        val uploads = viewModel.recentUploads.collectAsStateWithLifecycle().value

        ModalBottomSheet(
            onDismissRequest = { recentUploadSheetOpen = false },
            modifier = Modifier.statusBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.preference_uploader_recent_uploads_title),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = { confirmClearDialog = true },
                enabled = uploads.isNotEmpty(),
                content = { Text(stringResource(R.string.recent_uploads_clear)) }
            )
            LazyColumn {
                items(uploads) { upload ->
                    RecentUploadItem(upload)
                }
            }
        }

        if (confirmClearDialog) {
            AlertDialog(
                onDismissRequest = { confirmClearDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearUploads() },
                        content = { Text(stringResource(R.string.clear)) },
                    )
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirmClearDialog = false },
                        content = { Text(stringResource(R.string.dialog_cancel)) },
                    )
                },
                title = { Text(stringResource(R.string.clear_recent_uploads_dialog_title)) },
                text = { Text(stringResource(R.string.clear_recent_uploads_dialog_message)) },
            )
        }
    }
}

@Composable
fun RecentUploadItem(upload: RecentUpload) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        val clipboardManager = LocalClipboardManager.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(8.dp)
                .height(IntrinsicSize.Min),
        ) {
            OutlinedCard {
                AsyncImage(
                    modifier = Modifier
                        .background(CardDefaults.cardColors().containerColor)
                        .size(96.dp),
                    model = upload.imageUrl,
                    contentDescription = upload.imageUrl,
                    contentScale = ContentScale.Inside,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val link = buildAnnotatedString {
                        withLink(link = buildLinkAnnotation(upload.imageUrl)) {
                            append(upload.imageUrl)
                        }
                    }
                    Text(
                        text = link,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(upload.imageUrl)) },
                        content = {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.emote_sheet_copy))
                        },
                    )
                }
                if (upload.deleteUrl != null) {
                    val deletionText = stringResource(R.string.recent_upload_deletion_link, upload.deleteUrl)
                    val annotatedDeletionText = AnnotatedString.rememberAutoLinkText(
                        text = deletionText,
                        defaultLinkStyles = textLinkStyles(),
                    )
                    Text(annotatedDeletionText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = upload.formattedUploadTime,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun TextToSpeechCategory(
    settings: ToolsSettingsState,
    onInteraction: (ToolsSettingsInteraction) -> Unit,
    onNavToTTSUserIgnoreList: () -> Unit,
) {
    PreferenceCategory(title = stringResource(R.string.preference_tts_header)) {
        val context = LocalContext.current
        val checkTTSDataLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            when {
                it.resultCode != TextToSpeech.Engine.CHECK_VOICE_DATA_PASS -> context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                else                                                       -> onInteraction(ToolsSettingsInteraction.TTSEnabled(true))
            }
        }
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_tts_title),
            summary = stringResource(R.string.preference_tts_summary),
            isChecked = settings.ttsEnabled,
            onClick = {
                when {
                    it -> checkTTSDataLauncher.launch(Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))
                    else -> onInteraction(ToolsSettingsInteraction.TTSEnabled(false))
                }
            },
        )

        val queueSummary = stringResource(R.string.preference_tts_queue_add)
        val newestSummary = stringResource(R.string.preference_tts_queue_flush)
        val modeEntries = remember { listOf(queueSummary, newestSummary).toImmutableList() }
        PreferenceListDialog(
            title = stringResource(R.string.preference_tts_queue_title),
            summary = modeEntries[settings.ttsPlayMode.ordinal],
            values = TTSPlayMode.entries.toImmutableList(),
            entries = modeEntries,
            selected = settings.ttsPlayMode,
            isEnabled = settings.ttsEnabled,
            onChanged = { onInteraction(ToolsSettingsInteraction.TTSMode(it)) },
        )

        val formatMessage = stringResource(R.string.preference_tts_message_format_message)
        val formatUserAndMessage = stringResource(R.string.preference_tts_message_format_combined)
        val formatEntries = remember { listOf(formatMessage, formatUserAndMessage).toImmutableList() }
        PreferenceListDialog(
            title = stringResource(R.string.preference_tts_message_format_title),
            summary = formatEntries[settings.ttsMessageFormat.ordinal],
            values = TTSMessageFormat.entries.toImmutableList(),
            entries = formatEntries,
            selected = settings.ttsMessageFormat,
            isEnabled = settings.ttsEnabled,
            onChanged = { onInteraction(ToolsSettingsInteraction.TTSFormat(it)) },
        )

        SwitchPreferenceItem(
            title = stringResource(R.string.preference_tts_force_english_title),
            summary = stringResource(R.string.preference_tts_force_english_summary),
            isChecked = settings.ttsForceEnglish,
            isEnabled = settings.ttsEnabled,
            onClick = { onInteraction(ToolsSettingsInteraction.TTSForceEnglish(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_tts_message_ignore_url_title),
            summary = stringResource(R.string.preference_tts_message_ignore_url_message),
            isChecked = settings.ttsIgnoreUrls,
            isEnabled = settings.ttsEnabled,
            onClick = { onInteraction(ToolsSettingsInteraction.TTSIgnoreUrls(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_tts_message_ignore_emote_title),
            summary = stringResource(R.string.preference_tts_message_ignore_emote_message),
            isChecked = settings.ttsIgnoreEmotes,
            isEnabled = settings.ttsEnabled,
            onClick = { onInteraction(ToolsSettingsInteraction.TTSIgnoreEmotes(it)) },
        )
        PreferenceItem(
            title = stringResource(R.string.preference_tts_user_ignore_list_title),
            summary = stringResource(R.string.preference_tts_user_ignore_list_summary),
            onClick = onNavToTTSUserIgnoreList,
            isEnabled = settings.ttsEnabled,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )
    }
}
