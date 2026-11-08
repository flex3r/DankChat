package com.flxrs.dankchat.preferences.tools.export

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.ExpandablePreferenceItem
import com.flxrs.dankchat.preferences.components.PreferenceCategory
import com.flxrs.dankchat.utils.compose.ConfirmationBottomSheet
import com.flxrs.dankchat.utils.compose.ContentAlpha
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import com.jakewharton.processphoenix.ProcessPhoenix
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private const val CLIP_LABEL = "DankChat"
private const val CHANNELS_FILE_NAME = "dankchat-channels.json"
private const val JSON_MIME_TYPE = "application/json"

// Some file managers report json files as generic binary
private val IMPORT_MIME_TYPES = arrayOf(JSON_MIME_TYPE, "application/octet-stream")

@Composable
fun DataExportCategory(snackbarHostState: SnackbarHostState) {
    val viewModel = koinViewModel<DataExportViewModel>()
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val pasteFromClipboard: ((String?) -> Unit) -> Unit = { onText ->
        scope.launch { onText(clipboard.getClipEntry()?.clipData?.firstText(context)) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message =
                when (event) {
                    is DataExportEvent.ChannelsExported -> resources.getQuantityString(R.plurals.channels_exported, event.count, event.count)

                    is DataExportEvent.ChannelsImported -> resources.getQuantityString(R.plurals.channels_imported, event.count, event.count)

                    DataExportEvent.NoNewChannels -> resources.getString(R.string.channels_import_none_new)

                    DataExportEvent.SettingsExported -> resources.getString(R.string.export_data_done)

                    DataExportEvent.ExportFailed -> resources.getString(R.string.export_data_failed)

                    DataExportEvent.ImportFailed -> resources.getString(R.string.import_data_failed)

                    DataExportEvent.ClipboardInvalid -> resources.getString(R.string.clipboard_no_export)

                    is DataExportEvent.CopyToClipboard -> {
                        clipboard.setClipEntry(ClipEntry(backupClipData(event.text, event.isSensitive)))
                        resources.getString(R.string.share_upload_copied)
                    }

                    DataExportEvent.Restart -> {
                        ProcessPhoenix.triggerRebirth(context)
                        return@collect
                    }
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    val exportChannelsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)) { uri ->
            uri?.let(viewModel::exportChannels)
        }
    val importChannelsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importChannels)
        }
    val exportSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)) { uri ->
            uri?.let(viewModel::exportSettings)
        }
    val importSettingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::readSettings)
        }

    PreferenceCategory(title = stringResource(R.string.preference_export_header)) {
        BackupPreferenceItem(
            title = stringResource(R.string.backup_channels_title),
            description = stringResource(R.string.backup_channels_description),
            isExportEnabled = state.hasChannels,
            onExport = { exportChannelsLauncher.launch(CHANNELS_FILE_NAME) },
            onImport = { importChannelsLauncher.launch(IMPORT_MIME_TYPES) },
            onCopy = viewModel::copyChannels,
            onPaste = { pasteFromClipboard(viewModel::pasteChannels) },
        )
        BackupPreferenceItem(
            title = stringResource(R.string.settings),
            description = stringResource(R.string.backup_settings_description),
            onExport = { exportSettingsLauncher.launch(viewModel.settingsFileName()) },
            onImport = { importSettingsLauncher.launch(IMPORT_MIME_TYPES) },
            onCopy = viewModel::copySettings,
            onPaste = { pasteFromClipboard(viewModel::pasteSettings) },
        )
    }

    if (state.isConfirmingImport) {
        ConfirmationBottomSheet(
            title = stringResource(R.string.import_data_confirmation),
            confirmText = stringResource(R.string.backup_import),
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImport,
        )
    }
}

@Composable
private fun BackupPreferenceItem(
    title: String,
    description: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    isExportEnabled: Boolean = true,
) {
    ExpandablePreferenceItem(title = title) {
        val scope = rememberCoroutineScope()
        val sheetState = rememberModalSheetState()
        val hideAndRun: (() -> Unit) -> Unit = { action ->
            scope.launch {
                sheetState.hide()
                action()
                dismiss()
            }
        }
        ModalBottomSheet(
            onDismissRequest = ::dismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            BackupActionItem(
                icon = Icons.Outlined.FileUpload,
                text = stringResource(R.string.backup_export),
                isEnabled = isExportEnabled,
                onClick = { hideAndRun(onExport) },
            )
            BackupActionItem(
                icon = Icons.Outlined.FileDownload,
                text = stringResource(R.string.backup_import),
                onClick = { hideAndRun(onImport) },
            )
            BackupActionItem(
                icon = Icons.Outlined.ContentCopy,
                text = stringResource(R.string.backup_copy),
                isEnabled = isExportEnabled,
                onClick = { hideAndRun(onCopy) },
            )
            BackupActionItem(
                icon = Icons.Outlined.ContentPaste,
                text = stringResource(R.string.backup_paste),
                onClick = { hideAndRun(onPaste) },
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// Matches the rows of PreferenceListDialog, with the icon in place of the radio button
@Composable
private fun BackupActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
) {
    val color =
        when {
            isEnabled -> LocalContentColor.current
            else -> LocalContentColor.current.copy(alpha = ContentAlpha.disabled)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = isEnabled, onClick = onClick)
                .padding(horizontal = 16.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = null, tint = color)
        }
        Text(
            text = text,
            color = color,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 18.sp,
        )
    }
}

// Marked sensitive so the system clipboard preview hides it, settings can contain uploader keys
private fun backupClipData(
    text: String,
    isSensitive: Boolean,
): ClipData {
    val clipData = ClipData.newPlainText(CLIP_LABEL, text)
    if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clipData.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    return clipData
}

private fun ClipData.firstText(context: Context): String? = takeIf { itemCount > 0 }
    ?.getItemAt(0)
    ?.coerceToText(context)
    ?.toString()
