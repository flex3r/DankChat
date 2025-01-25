package com.flxrs.dankchat.preferences.tools.upload

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.tools.ImageUploaderConfig
import com.flxrs.dankchat.utils.compose.textLinkStyles
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.autolinktext.rememberAutoLinkText

@Composable
fun ImageUploaderScreen(onNavBack: () -> Unit) {
    val viewModel = koinViewModel<ImageUploaderViewModel>()
    val uploader = viewModel.uploader.collectAsStateWithLifecycle().value
    ImageUploaderScreen(
        uploaderConfig = uploader,
        onReset = { viewModel.reset() },
        onSave = { viewModel.save(it) },
        onSaveAndNavBack = {
            viewModel.save(it)
            onNavBack()
        },
    )
}

@Composable
private fun ImageUploaderScreen(
    uploaderConfig: ImageUploaderConfig,
    onReset: () -> Unit,
    onSave: (ImageUploaderConfig) -> Unit,
    onSaveAndNavBack: (ImageUploaderConfig) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uploadUrl = rememberTextFieldState(uploaderConfig.uploadUrl)
    val formField = rememberTextFieldState(uploaderConfig.formField)
    val headers = rememberTextFieldState(uploaderConfig.headers.orEmpty())
    val linkPattern = rememberTextFieldState(uploaderConfig.imageLinkPattern.orEmpty())
    val deleteLinkPattern = rememberTextFieldState(uploaderConfig.deletionLinkPattern.orEmpty())
    val hasChanged = remember {
        derivedStateOf {
            uploaderConfig.uploadUrl != uploadUrl.text ||
                    uploaderConfig.formField != formField.text ||
                    uploaderConfig.headers.orEmpty() != headers.text ||
                    uploaderConfig.imageLinkPattern.orEmpty() != linkPattern.text ||
                    uploaderConfig.deletionLinkPattern.orEmpty() != deleteLinkPattern.text
        }
    }

    var resetDialog by remember { mutableStateOf(false) }
    val currentConfig = {
        ImageUploaderConfig(
            uploadUrl = uploadUrl.text.toString(),
            formField = formField.text.toString(),
            headers = headers.text.toString(),
            imageLinkPattern = linkPattern.text.toString(),
            deletionLinkPattern = deleteLinkPattern.text.toString(),
        )
    }

    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            onSave(currentConfig())
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_uploader_configure_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { onSaveAndNavBack(currentConfig()) },
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val description = AnnotatedString.rememberAutoLinkText(
                text = stringResource(R.string.uploader_description),
                defaultLinkStyles = textLinkStyles(),
            )
            Text(description, style = MaterialTheme.typography.bodyMedium)

            TextButton(
                onClick = { resetDialog = true },
                modifier = Modifier.align(Alignment.End),
                content = { Text(stringResource(R.string.uploader_reset)) },
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                state = uploadUrl,
                label = { Text(stringResource(R.string.uploader_url)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                state = formField,
                label = { Text(stringResource(R.string.uploader_field)) },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                state = headers,
                label = { Text(stringResource(R.string.uploader_headers)) },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                state = linkPattern,
                label = { Text(stringResource(R.string.uploader_image_link)) },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                state = deleteLinkPattern,
                label = { Text(stringResource(R.string.uploader_deletion_link)) },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                lineLimits = TextFieldLineLimits.SingleLine,
            )

            AnimatedVisibility(visible = hasChanged.value) {
                Button(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    onClick = { onSaveAndNavBack(currentConfig()) },
                    content = { Text(stringResource(R.string.save)) },
                )
            }
            NavigationBarSpacer()
        }
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text(stringResource(R.string.reset_media_uploader_dialog_title)) },
            text = { Text(stringResource(R.string.reset_media_uploader_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetDialog = false
                        onReset()
                    },
                    content = { Text(stringResource(R.string.reset_media_uploader_dialog_positive)) },
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { resetDialog = false },
                    content = { Text(stringResource(R.string.dialog_cancel)) },
                )
            },
        )
    }
}
