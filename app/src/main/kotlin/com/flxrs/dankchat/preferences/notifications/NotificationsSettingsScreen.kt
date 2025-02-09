package com.flxrs.dankchat.preferences.notifications

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceCategory
import com.flxrs.dankchat.preferences.components.PreferenceItem
import com.flxrs.dankchat.preferences.components.PreferenceListDialog
import com.flxrs.dankchat.preferences.components.SwitchPreferenceItem
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationsSettingsScreen(
    onNavToHighlights: () -> Unit,
    onNavToIgnores: () -> Unit,
    onNavBack: () -> Unit,
) {
    val viewModel = koinViewModel<NotificationsSettingsViewModel>()
    val settings = viewModel.settings.collectAsStateWithLifecycle().value
    NotificationsSettingsScreen(
        settings = settings,
        onInteraction = { viewModel.onInteraction(it) },
        onNavToHighlights = onNavToHighlights,
        onNavToIgnores = onNavToIgnores,
        onNavBack = onNavBack,
    )
}

@Composable
private fun NotificationsSettingsScreen(
    settings: NotificationsSettings,
    onInteraction: (NotificationsSettingsInteraction) -> Unit,
    onNavToHighlights: () -> Unit,
    onNavToIgnores: () -> Unit,
    onNavBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_highlights_ignores_header)) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            NotificationsCategory(
                showNotifications = settings.showNotifications,
                showWhisperNotifications = settings.showWhisperNotifications,
                onInteraction = onInteraction,
            )
            HorizontalDivider(thickness = Dp.Hairline)
            MentionsCategory(
                mentionFormat = settings.mentionFormat,
                onInteraction = onInteraction,
                onNavToHighlights = onNavToHighlights,
                onNavToIgnores = onNavToIgnores,
            )
            NavigationBarSpacer()
        }
    }
}

@Composable
fun NotificationsCategory(
    showNotifications: Boolean,
    showWhisperNotifications: Boolean,
    onInteraction: (NotificationsSettingsInteraction) -> Unit,
) {
    PreferenceCategory(title = stringResource(R.string.preference_notification_header)) {
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_notification_title),
            summary = stringResource(R.string.preference_notification_summary),
            isChecked = showNotifications,
            onClick = { onInteraction(NotificationsSettingsInteraction.Notifications(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_notification_whisper_title),
            isChecked = showWhisperNotifications,
            isEnabled = showNotifications,
            onClick = { onInteraction(NotificationsSettingsInteraction.WhisperNotifications(it)) },
        )
    }
}

@Composable
fun MentionsCategory(
    mentionFormat: MentionFormat,
    onInteraction: (NotificationsSettingsInteraction) -> Unit,
    onNavToHighlights: () -> Unit,
    onNavToIgnores: () -> Unit,
) {
    PreferenceCategory(title = stringResource(R.string.mentions)) {
        val entries = remember { MentionFormat.entries.map { it.template }.toImmutableList() }
        PreferenceListDialog(
            title = stringResource(R.string.preference_mention_format_title),
            summary = mentionFormat.template,
            values = MentionFormat.entries.toImmutableList(),
            entries = entries,
            selected = mentionFormat,
            onChanged = { onInteraction(NotificationsSettingsInteraction.Mention(it)) },
        )

        PreferenceItem(
            title = stringResource(R.string.highlights),
            summary = stringResource(R.string.preference_highlights_summary),
            onClick = onNavToHighlights,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )
        PreferenceItem(
            title = stringResource(R.string.ignores),
            summary = stringResource(R.string.preference_ignores_summary),
            onClick = onNavToIgnores,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )
    }
}
