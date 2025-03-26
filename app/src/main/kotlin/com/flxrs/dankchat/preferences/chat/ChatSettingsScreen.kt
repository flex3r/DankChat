package com.flxrs.dankchat.preferences.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceCategory
import com.flxrs.dankchat.preferences.components.PreferenceItem
import com.flxrs.dankchat.preferences.components.PreferenceListDialog
import com.flxrs.dankchat.preferences.components.PreferenceMultiListDialog
import com.flxrs.dankchat.preferences.components.SliderPreferenceItem
import com.flxrs.dankchat.preferences.components.SwitchPreferenceItem
import com.jakewharton.processphoenix.ProcessPhoenix
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun ChatSettingsScreen(
    onNavToCommands: () -> Unit,
    onNavToUserDisplays: () -> Unit,
    onNavBack: () -> Unit,
) {
    val viewModel = koinViewModel<ChatSettingsViewModel>()
    val settings = viewModel.settings.collectAsStateWithLifecycle().value
    val restartRequiredTitle = stringResource(R.string.restart_required)
    val restartRequiredAction = stringResource(R.string.restart)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest {
            when (it) {
                ChatSettingsEvent.RestartRequired -> {
                    val result = snackbarHostState.showSnackbar(
                        message = restartRequiredTitle,
                        actionLabel = restartRequiredAction,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        ProcessPhoenix.triggerRebirth(context)
                    }
                }
            }
        }
    }

    ChatSettingsScreen(
        settings = settings,
        snackbarHostState = snackbarHostState,
        onInteraction = { viewModel.onInteraction(it) },
        onNavToCommands = onNavToCommands,
        onNavToUserDisplays = onNavToUserDisplays,
        onNavBack = onNavBack,
    )
}

@Composable
private fun ChatSettingsScreen(
    settings: ChatSettingsState,
    snackbarHostState: SnackbarHostState,
    onInteraction: (ChatSettingsInteraction) -> Unit,
    onNavToCommands: () -> Unit,
    onNavToUserDisplays: () -> Unit,
    onNavBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_chat_header)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavBack,
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            GeneralCategory(
                suggestions = settings.suggestions,
                preferEmoteSuggestions = settings.preferEmoteSuggestions,
                supibotSuggestions = settings.supibotSuggestions,
                animateGifs = settings.animateGifs,
                scrollbackLength = settings.scrollbackLength,
                showUsernames = settings.showUsernames,
                userLongClickBehavior = settings.userLongClickBehavior,
                showTimedOutMessages = settings.showTimedOutMessages,
                showTimestamps = settings.showTimestamps,
                timestampFormat = settings.timestampFormat,
                visibleBadges = settings.visibleBadges,
                visibleEmotes = settings.visibleEmotes,
                onNavToCommands = onNavToCommands,
                onNavToUserDisplays = onNavToUserDisplays,
                onInteraction = onInteraction,
            )
            HorizontalDivider(thickness = Dp.Hairline)
            SevenTVCategory(
                enabled = VisibleThirdPartyEmotes.SevenTV in settings.visibleEmotes,
                allowUnlistedSevenTvEmotes = settings.allowUnlistedSevenTvEmotes,
                sevenTVLiveEmoteUpdates = settings.sevenTVLiveEmoteUpdates,
                sevenTVLiveEmoteUpdatesBehavior = settings.sevenTVLiveEmoteUpdatesBehavior,
                onInteraction = onInteraction,
            )
            HorizontalDivider(thickness = Dp.Hairline)
            MessageHistoryCategory(
                loadMessageHistory = settings.loadMessageHistory,
                loadMessageHistoryAfterReconnect = settings.loadMessageHistoryAfterReconnect,
                messageHistoryDashboardUrl = settings.messageHistoryDashboardUrl,
                onInteraction = onInteraction,
            )
            HorizontalDivider(thickness = Dp.Hairline)
            ChannelDataCategory(
                showChatModes = settings.showChatModes,
                onInteraction = onInteraction,
            )
            NavigationBarSpacer()
        }
    }
}

@Composable
private fun GeneralCategory(
    suggestions: Boolean,
    preferEmoteSuggestions: Boolean,
    supibotSuggestions: Boolean,
    animateGifs: Boolean,
    scrollbackLength: Int,
    showUsernames: Boolean,
    userLongClickBehavior: UserLongClickBehavior,
    showTimedOutMessages: Boolean,
    showTimestamps: Boolean,
    timestampFormat: String,
    visibleBadges: ImmutableList<VisibleBadges>,
    visibleEmotes: ImmutableList<VisibleThirdPartyEmotes>,
    onNavToCommands: () -> Unit,
    onNavToUserDisplays: () -> Unit,
    onInteraction: (ChatSettingsInteraction) -> Unit,
) {
    PreferenceCategory(title = stringResource(R.string.preference_general_header)) {
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_suggestions_title),
            summary = stringResource(R.string.preference_suggestions_summary),
            isChecked = suggestions,
            onClick = { onInteraction(ChatSettingsInteraction.Suggestions(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_prefer_emote_suggestions_title),
            summary = stringResource(R.string.preference_prefer_emote_suggestions_summary),
            isChecked = preferEmoteSuggestions,
            onClick = { onInteraction(ChatSettingsInteraction.PreferEmoteSuggestions(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_supibot_suggestions_title),
            isChecked = supibotSuggestions,
            onClick = { onInteraction(ChatSettingsInteraction.SupibotSuggestions(it)) },
        )
        PreferenceItem(
            title = stringResource(R.string.commands_title),
            onClick = onNavToCommands,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )

        SwitchPreferenceItem(
            title = stringResource(R.string.preference_animate_gifs_title),
            isChecked = animateGifs,
            onClick = { onInteraction(ChatSettingsInteraction.AnimateGifs(it)) },
        )

        var sliderValue by remember(scrollbackLength) { mutableFloatStateOf(scrollbackLength.toFloat()) }
        SliderPreferenceItem(
            title = stringResource(R.string.preference_scrollback_length_title),
            value = sliderValue,
            range = 50f..1000f,
            steps = 18,
            onDrag = { sliderValue = it },
            onDragFinished = { onInteraction(ChatSettingsInteraction.ScrollbackLength(sliderValue.roundToInt())) },
            displayValue = false,
            summary = sliderValue.roundToInt().toString(),
        )

        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_username_title),
            isChecked = showUsernames,
            onClick = { onInteraction(ChatSettingsInteraction.ShowUsernames(it)) },
        )

        val longClickSummaryOn = stringResource(R.string.preference_user_long_click_summary_on)
        val longClickSummaryOff = stringResource(R.string.preference_user_long_click_summary_off)
        val longClickEntries = remember { listOf(longClickSummaryOn, longClickSummaryOff).toImmutableList() }
        PreferenceListDialog(
            title = stringResource(R.string.preference_user_long_click_title),
            summary = longClickEntries[userLongClickBehavior.ordinal],
            values = UserLongClickBehavior.entries.toImmutableList(),
            entries = longClickEntries,
            selected = userLongClickBehavior,
            onChanged = { onInteraction(ChatSettingsInteraction.UserLongClick(it)) },
        )

        PreferenceItem(
            title = stringResource(R.string.custom_user_display_title),
            summary = stringResource(R.string.custom_user_display_summary),
            onClick = onNavToUserDisplays,
            trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
        )

        SwitchPreferenceItem(
            title = stringResource(R.string.preference_show_timed_out_messages_title),
            isChecked = showTimedOutMessages,
            onClick = { onInteraction(ChatSettingsInteraction.ShowTimedOutMessages(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_timestamp_title),
            isChecked = showTimestamps,
            onClick = { onInteraction(ChatSettingsInteraction.ShowTimestamps(it)) },
        )
        val timestampFormats = stringArrayResource(R.array.timestamp_formats).toImmutableList()
        PreferenceListDialog(
            title = stringResource(R.string.preference_timestamp_format_title),
            summary = timestampFormat,
            values = timestampFormats,
            entries = timestampFormats,
            selected = timestampFormat,
            onChanged = { onInteraction(ChatSettingsInteraction.TimestampFormat(it)) },
        )

        val entries = stringArrayResource(R.array.badges_entries)
            .plus(stringResource(R.string.shared_chat))
            .toImmutableList()

        PreferenceMultiListDialog(
            title = stringResource(R.string.preference_visible_badges_title),
            initialSelected = visibleBadges,
            values = VisibleBadges.entries.toImmutableList(),
            entries = entries,
            onChanged = { onInteraction(ChatSettingsInteraction.Badges(it)) },
        )
        PreferenceMultiListDialog(
            title = stringResource(R.string.preference_visible_emotes_title),
            initialSelected = visibleEmotes,
            values = VisibleThirdPartyEmotes.entries.toImmutableList(),
            entries = stringArrayResource(R.array.emotes_entries).toImmutableList(),
            onChanged = { onInteraction(ChatSettingsInteraction.Emotes(it)) },
        )
    }
}

@Composable
private fun SevenTVCategory(
    enabled: Boolean,
    allowUnlistedSevenTvEmotes: Boolean,
    sevenTVLiveEmoteUpdates: Boolean,
    sevenTVLiveEmoteUpdatesBehavior: LiveUpdatesBackgroundBehavior,
    onInteraction: (ChatSettingsInteraction) -> Unit,
) {
    PreferenceCategory(title = stringResource(R.string.preference_7tv_category_title)) {
        SwitchPreferenceItem(
            isEnabled = enabled,
            title = stringResource(R.string.preference_unlisted_emotes_title),
            summary = stringResource(R.string.preference_unlisted_emotes_summary),
            isChecked = allowUnlistedSevenTvEmotes,
            onClick = { onInteraction(ChatSettingsInteraction.AllowUnlisted(it)) },
        )
        SwitchPreferenceItem(
            isEnabled = enabled,
            title = stringResource(R.string.preference_7tv_live_updates_title),
            isChecked = sevenTVLiveEmoteUpdates,
            onClick = { onInteraction(ChatSettingsInteraction.LiveEmoteUpdates(it)) },
        )
        val liveUpdateEntries = stringArrayResource(R.array.event_api_timeout_entries).toImmutableList()
        val summary = when (sevenTVLiveEmoteUpdatesBehavior) {
            LiveUpdatesBackgroundBehavior.Never -> stringResource(R.string.preference_7tv_live_updates_timeout_summary_never_active)
            LiveUpdatesBackgroundBehavior.Always -> stringResource(R.string.preference_7tv_live_updates_timeout_summary_always_active)
            else -> stringResource(R.string.preference_7tv_live_updates_timeout_summary_timeout, liveUpdateEntries[sevenTVLiveEmoteUpdatesBehavior.ordinal])
        }
        PreferenceListDialog(
            isEnabled = enabled && sevenTVLiveEmoteUpdates,
            title = stringResource(R.string.preference_7tv_live_updates_timeout_title),
            summary = summary,
            values = LiveUpdatesBackgroundBehavior.entries.toImmutableList(),
            entries = liveUpdateEntries,
            selected = sevenTVLiveEmoteUpdatesBehavior,
            onChanged = { onInteraction(ChatSettingsInteraction.LiveEmoteUpdatesBehavior(it)) },
        )
    }
}

@Composable
private fun MessageHistoryCategory(
    loadMessageHistory: Boolean,
    loadMessageHistoryAfterReconnect: Boolean,
    messageHistoryDashboardUrl: String,
    onInteraction: (ChatSettingsInteraction) -> Unit,
) {
    val launcher = LocalUriHandler.current
    PreferenceCategory(title = stringResource(R.string.preference_message_history_header)) {
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_load_message_history_title),
            isChecked = loadMessageHistory,
            onClick = { onInteraction(ChatSettingsInteraction.MessageHistory(it)) },
        )
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_load_messages_on_reconnect_title),
            summary = stringResource(R.string.preference_load_messages_on_reconnect_summary),
            isChecked = loadMessageHistoryAfterReconnect,
            onClick = { onInteraction(ChatSettingsInteraction.MessageHistoryAfterReconnect(it)) },
        )
        PreferenceItem(
            title = stringResource(R.string.preference_message_history_dashboard_title),
            summary = stringResource(R.string.preference_message_history_dashboard_summary),
            onClick = { launcher.openUri(messageHistoryDashboardUrl) },
        )
    }
}

@Composable
private fun ChannelDataCategory(
    showChatModes: Boolean,
    onInteraction: (ChatSettingsInteraction) -> Unit,
) {
    PreferenceCategory(title = stringResource(R.string.preference_channel_data_header)) {
        SwitchPreferenceItem(
            title = stringResource(R.string.preference_roomstate_title),
            summary = stringResource(R.string.preference_roomstate_summary),
            isChecked = showChatModes,
            onClick = { onInteraction(ChatSettingsInteraction.ChatModes(it)) },
        )
    }
}
