package com.flxrs.dankchat.preferences.stream

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.SwitchPreferenceItem
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StreamsSettingsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<StreamsSettingsViewModel>()
    val settings = viewModel.settings.collectAsStateWithLifecycle().value

    StreamsSettingsContent(
        settings = settings,
        onInteraction = { viewModel.onInteraction(it) },
        onBack = onBack,
    )
}

@Composable
private fun StreamsSettingsContent(
    settings: StreamsSettings,
    onInteraction: (StreamsSettingsInteraction) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_streams_header)) },
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
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_fetch_streams_title),
                summary = stringResource(R.string.preference_fetch_streams_summary),
                isChecked = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.FetchStreams(it)) },
            )
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_streaminfo_title),
                summary = stringResource(R.string.preference_streaminfo_summary),
                isChecked = settings.showStreamInfo,
                isEnabled = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.ShowStreamInfo(it)) },
            )
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_streaminfo_category_title),
                summary = stringResource(R.string.preference_streaminfo_category_summary),
                isChecked = settings.showStreamCategory,
                isEnabled = settings.fetchStreams && settings.showStreamInfo,
                onClick = { onInteraction(StreamsSettingsInteraction.ShowStreamCategory(it)) },
            )
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_stream_live_messages_title),
                summary = stringResource(R.string.preference_stream_live_messages_summary),
                isChecked = settings.showLiveMessages,
                isEnabled = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.ShowLiveMessages(it)) },
            )
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_stream_extensions_title),
                summary = stringResource(R.string.preference_stream_extensions_summary),
                isChecked = settings.showStreamExtensions,
                isEnabled = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.ShowStreamExtensions(it)) },
            )
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_retain_webview_title),
                summary = stringResource(R.string.preference_retain_webview_summary),
                isChecked = settings.preventStreamReloads,
                isEnabled = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.PreventStreamReloads(it)) },
            )
            SwitchPreferenceItem(
                title = stringResource(R.string.preference_vaft_enabled_title),
                summary = stringResource(R.string.preference_vaft_enabled_summary),
                isChecked = settings.vaftEnabled,
                isEnabled = settings.fetchStreams,
                onClick = { onInteraction(StreamsSettingsInteraction.VaftEnabled(it)) },
            )

            val activity = LocalActivity.current
            val pipAvailable =
                remember {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        activity != null && activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                }
            if (pipAvailable) {
                SwitchPreferenceItem(
                    title = stringResource(R.string.preference_pip_title),
                    summary = stringResource(R.string.preference_pip_summary),
                    isChecked = settings.enablePiP,
                    isEnabled = settings.fetchStreams && settings.preventStreamReloads,
                    onClick = { onInteraction(StreamsSettingsInteraction.EnablePiP(it)) },
                )
            }
            NavigationBarSpacer()
        }
    }
}
