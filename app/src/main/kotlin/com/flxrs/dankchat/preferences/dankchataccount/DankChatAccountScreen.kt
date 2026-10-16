package com.flxrs.dankchat.preferences.dankchataccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceCategory
import com.flxrs.dankchat.preferences.components.PreferenceItem
import com.flxrs.dankchat.preferences.components.PreferenceSummary
import com.flxrs.dankchat.ui.chat.messages.common.launchCustomTab
import com.flxrs.dankchat.utils.compose.ContentAlpha
import com.flxrs.dankchat.utils.compose.CustomTabsWarmupEffect
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DankChatAccountScreen(onNavBack: () -> Unit) {
    val viewModel = koinViewModel<DankChatAccountViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val events = remember(viewModel) { DankChatAccountEventsWrapper(viewModel.events) }

    DankChatAccountScreen(
        state = state,
        eventsWrapper = events,
        onChoose = viewModel::choose,
        onRetry = viewModel::load,
        onManageAccount = viewModel::manageAccount,
        onNavBack = onNavBack,
    )

    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }
    CustomTabsWarmupEffect()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DankChatAccountScreen(
    state: DankChatAccountState,
    eventsWrapper: DankChatAccountEventsWrapper,
    onChoose: (DankChatBadgeChoice) -> Unit,
    onRetry: () -> Unit,
    onManageAccount: () -> Unit,
    onNavBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val saveFailedMessage = stringResource(R.string.dankchat_badge_save_failed)

    LaunchedEffect(eventsWrapper) {
        eventsWrapper.events.collect {
            when (it) {
                DankChatAccountEvent.SaveFailed -> snackbarHost.showSnackbar(saveFailedMessage)
                is DankChatAccountEvent.OpenUrl -> launchCustomTab(context, it.url)
            }
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost, modifier = Modifier.navigationBarsPadding()) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.preference_dankchat_account_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavBack,
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                },
            )
        },
    ) { padding ->
        when (state.badge) {
            DankChatBadgeState.Loading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState()),
                ) {
                    BadgeCategory(state = state.badge, onChoose = onChoose, onRetry = onRetry)
                    PreferenceCategory(title = stringResource(R.string.account)) {
                        PreferenceItem(
                            title = stringResource(R.string.dankchat_account_manage_title),
                            summary = stringResource(R.string.dankchat_account_manage_summary),
                            isEnabled = !state.isOpeningAccount,
                            onClick = onManageAccount,
                        )
                    }
                    NavigationBarSpacer()
                }
            }
        }
    }
}

@Composable
private fun BadgeCategory(
    state: DankChatBadgeState,
    onChoose: (DankChatBadgeChoice) -> Unit,
    onRetry: () -> Unit,
) {
    PreferenceCategory(title = stringResource(R.string.badge)) {
        when (state) {
            is DankChatBadgeState.Loaded -> BadgeChoices(state = state, onChoose = onChoose)

            DankChatBadgeState.NoBadges -> BadgeMessage(stringResource(R.string.dankchat_badge_empty))

            DankChatBadgeState.LoggedOut -> BadgeMessage(stringResource(R.string.dankchat_badge_logged_out))

            DankChatBadgeState.Unsupported -> BadgeMessage(stringResource(R.string.dankchat_badge_unsupported))

            DankChatBadgeState.Failed -> {
                BadgeMessage(stringResource(R.string.dankchat_badge_load_failed))
                TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(stringResource(R.string.snackbar_retry))
                }
            }

            DankChatBadgeState.Loading -> Unit
        }
    }
}

@Composable
private fun BadgeChoices(
    state: DankChatBadgeState.Loaded,
    onChoose: (DankChatBadgeChoice) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        BadgeChoiceItem(
            title = stringResource(R.string.dankchat_badge_automatic),
            summary = stringResource(R.string.dankchat_badge_automatic_summary, state.automaticBadge.title),
            imageUrl = state.automaticBadge.url,
            isSelected = state.choice == DankChatBadgeChoice.Automatic,
            isEnabled = !state.isSaving,
            onClick = { onChoose(DankChatBadgeChoice.Automatic) },
        )
        state.badges.forEach { badge ->
            val choice = DankChatBadgeChoice.Badge(badge.id)
            BadgeChoiceItem(
                title = badge.title,
                imageUrl = badge.url,
                isSelected = state.choice == choice,
                isEnabled = !state.isSaving,
                onClick = { onChoose(choice) },
            )
        }
        BadgeChoiceItem(
            title = stringResource(R.string.dankchat_badge_none),
            imageUrl = null,
            isSelected = state.choice == DankChatBadgeChoice.Hidden,
            isEnabled = !state.isSaving,
            onClick = { onChoose(DankChatBadgeChoice.Hidden) },
        )
    }
}

@Composable
private fun BadgeChoiceItem(
    title: String,
    imageUrl: String?,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    summary: String? = null,
) {
    val contentColor = LocalContentColor.current
    val color =
        when {
            isEnabled -> contentColor
            else -> contentColor.copy(alpha = ContentAlpha.disabled)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = isSelected, enabled = isEnabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null, enabled = isEnabled)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.size(BADGE_SIZE)) {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier =
                Modifier
                    .padding(vertical = 16.dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = color)
            if (summary != null) {
                PreferenceSummary(summary, isEnabled)
            }
        }
    }
}

@Composable
private fun BadgeMessage(text: String) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        PreferenceSummary(text)
    }
}

private val BADGE_SIZE = 24.dp
