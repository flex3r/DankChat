package com.flxrs.dankchat.ui.main.sheet

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.preferences.components.DankBackground
import com.flxrs.dankchat.ui.chat.ChatScreen
import com.flxrs.dankchat.ui.chat.ChatScreenCallbacks
import com.flxrs.dankchat.ui.chat.ScrollDirectionTracker
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoViewModel
import com.flxrs.dankchat.ui.chat.history.HistoryChannel
import com.flxrs.dankchat.ui.chat.history.MessageHistoryViewModel
import com.flxrs.dankchat.ui.chat.message.MessageOptionsParams
import com.flxrs.dankchat.ui.chat.message.MessageOptionsViewModel
import com.flxrs.dankchat.ui.chat.user.UserPopupStateParams
import com.flxrs.dankchat.ui.chat.user.UserPopupViewModel
import com.flxrs.dankchat.ui.main.input.SuggestionDropdown
import com.flxrs.dankchat.utils.compose.bottomInsetsPadding
import com.flxrs.dankchat.utils.compose.predictiveBackScale
import com.flxrs.dankchat.utils.compose.selectedIndicatorBar
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MessageHistorySheet(
    viewModel: MessageHistoryViewModel,
    initialFilter: String,
    onDismiss: () -> Unit,
) {
    val emoteInfoViewModel: EmoteInfoViewModel = koinViewModel()
    val userPopupViewModel: UserPopupViewModel = koinViewModel()
    val messageOptionsViewModel: MessageOptionsViewModel = koinViewModel()

    var lastAppliedFilter by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialFilter) {
        if (lastAppliedFilter != initialFilter) {
            viewModel.setInitialQuery(initialFilter)
            lastAppliedFilter = initialFilter
        }
    }

    val displaySettings by viewModel.chatDisplaySettings.collectAsStateWithLifecycle()
    val messages by viewModel.historyUiStates.collectAsStateWithLifecycle(initialValue = persistentListOf())
    val filterSuggestions by viewModel.filterSuggestions.collectAsStateWithLifecycle()
    val availableChannels by viewModel.availableChannels.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val sheetBackgroundColor =
        lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            fraction = 0.75f,
        )
    var backProgress by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val toolbarTopPadding = statusBarHeight + 8.dp + 48.dp + 16.dp

    val ime = WindowInsets.ime
    val navBars = WindowInsets.navigationBars
    val navBarHeightDp = with(density) { navBars.getBottom(density).toDp() }
    // Resolved in the layout phase, the per-frame ime values must not be read in composition
    val imeAboveNavBars = remember(ime, navBars) { ime.exclude(navBars) }
    val targetImeDp = with(density) {
        (WindowInsets.imeAnimationTarget.getBottom(density) - navBars.getBottom(density)).coerceAtLeast(0).toDp()
    }

    var searchBarHeightPx by remember { mutableIntStateOf(0) }
    val searchBarHeightDp = with(density) { searchBarHeightPx.toDp() }

    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val sheetHeightDp = with(density) { sheetHeightPx.toDp() }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { event ->
                backProgress = event.progress
            }
            onDismiss()
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    var toolbarVisible by remember { mutableStateOf(true) }
    val scrollTracker =
        remember {
            ScrollDirectionTracker(
                hideThresholdPx = with(density) { 100.dp.toPx() },
                showThresholdPx = with(density) { 36.dp.toPx() },
                onHide = { toolbarVisible = false },
                onShow = { toolbarVisible = true },
            )
        }

    val scrollModifier = Modifier.nestedScroll(scrollTracker)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { sheetHeightPx = it.height }
                .background(sheetBackgroundColor)
                .graphicsLayer {
                    scaleX = 1f - (backProgress * 0.1f)
                    scaleY = 1f - (backProgress * 0.1f)
                    alpha = 1f - (backProgress * 0.5f)
                },
    ) {
        AnimatedContent(
            targetState = selectedChannel,
            transitionSpec = {
                (fadeIn(tween(300, delayMillis = 100)) + scaleIn(tween(300, delayMillis = 100), initialScale = 0.92f))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.92f))
            },
            label = "HistoryChannelSwitch",
            modifier = Modifier.fillMaxSize(),
        ) { channel ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bottomInsetsPadding(imeAboveNavBars),
            ) {
                ChatScreen(
                    messages = messages,
                    fontSize = displaySettings.fontSize,
                    callbacks =
                        ChatScreenCallbacks(
                            onUserClick = { userId, userName, displayName, ch, badges, _ ->
                                userPopupViewModel.show(
                                    UserPopupStateParams(
                                        targetUserId = userId?.let { UserId(it) },
                                        targetUserName = UserName(userName),
                                        targetDisplayName = DisplayName(displayName),
                                        channel = ch?.let { UserName(it) },
                                        badges = badges.map { it.badge },
                                    ),
                                )
                            },
                            onMessageLongClick = { messageId, ch, fullMessage ->
                                messageOptionsViewModel.show(
                                    MessageOptionsParams(
                                        messageId = messageId,
                                        channel = ch?.let { UserName(it) },
                                        fullMessage = fullMessage,
                                        canModerate = false,
                                        canCopy = true,
                                        canJump = true,
                                    ),
                                )
                            },
                            onEmoteClick = { emoteInfoViewModel.show(it) },
                        ),
                    animateGifs = displaySettings.animateGifs,
                    showChannelPrefix = channel is HistoryChannel.Global,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = toolbarTopPadding, bottom = searchBarHeightDp + navBarHeightDp),
                    scrollModifier = scrollModifier,
                    containerColor = sheetBackgroundColor,
                    onScrollToBottom = { toolbarVisible = true },
                )

                if (messages.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = searchBarHeightDp),
                    ) {
                        DankBackground(visible = true)
                        Text(
                            text = stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp, start = 32.dp, end = 32.dp),
                        )
                    }
                }
            }
        }

        var showChannelDropdown by remember { mutableStateOf(false) }
        var dropdownBackProgress by remember { mutableFloatStateOf(0f) }

        // Dismiss scrim — tap anywhere outside the dropdown closes it
        if (showChannelDropdown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { showChannelDropdown = false },
            )
        }

        SheetToolbar(
            visible = toolbarVisible,
            statusBarHeight = statusBarHeight,
            sheetBackgroundColor = sheetBackgroundColor,
            onBack = onDismiss,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clickable { showChannelDropdown = !showChannelDropdown }
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(start = 16.dp, end = 8.dp),
                ) {
                    Text(
                        text = when (selectedChannel) {
                            is HistoryChannel.Global -> stringResource(R.string.global_history_title)
                            is HistoryChannel.Channel -> stringResource(R.string.message_history_title, (selectedChannel as HistoryChannel.Channel).name.value)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Channel quick switcher dropdown — positioned below toolbar, outside SheetToolbar to avoid growing its gradient
        AnimatedVisibility(
            visible = showChannelDropdown && toolbarVisible,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = statusBarHeight + 8.dp + 48.dp + 4.dp)
                    .padding(start = 8.dp + 48.dp + 8.dp, end = 8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.predictiveBackScale(dropdownBackProgress),
            ) {
                PredictiveBackHandler(enabled = showChannelDropdown) { progress ->
                    try {
                        progress.collect { event ->
                            dropdownBackProgress = event.progress
                        }
                        showChannelDropdown = false
                    } catch (_: CancellationException) {
                        dropdownBackProgress = 0f
                    }
                }
                // Freeze the displayed selection while the dropdown is closing so the newly
                // picked row doesn't flash as selected before the exit animation completes.
                var frozenSelection by remember { mutableStateOf<HistoryChannel?>(null) }
                LaunchedEffect(showChannelDropdown) {
                    if (showChannelDropdown) {
                        frozenSelection = null
                    } else if (frozenSelection != null) {
                        delay(DROPDOWN_FREEZE_MS)
                        frozenSelection = null
                    }
                }
                val displaySelectedChannel = frozenSelection ?: selectedChannel
                Column(
                    modifier =
                        Modifier
                            .width(IntrinsicSize.Max)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                ) {
                    val indicatorColor = MaterialTheme.colorScheme.primary
                    availableChannels.forEach { channel ->
                        val isSelected = channel == displaySelectedChannel
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        frozenSelection = selectedChannel
                                        viewModel.selectChannel(channel)
                                        showChannelDropdown = false
                                    }.selectedIndicatorBar(isSelected, indicatorColor)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Text(
                                text = when (channel) {
                                    is HistoryChannel.Global -> stringResource(R.string.global_history_title)
                                    is HistoryChannel.Channel -> channel.name.value
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = when {
                                    isSelected -> FontWeight.Bold
                                    else -> FontWeight.Normal
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // Status bar fill when toolbar is hidden
        if (!toolbarVisible) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(statusBarHeight)
                        .background(sheetBackgroundColor.copy(alpha = 0.7f)),
            )
        }

        val sheetToolbarHeight = if (toolbarVisible) toolbarTopPadding else statusBarHeight
        val suggestionMaxHeight =
            (sheetHeightDp - targetImeDp - searchBarHeightDp - navBarHeightDp - sheetToolbarHeight - 8.dp)
                .coerceAtLeast(0.dp)
        SuggestionDropdown(
            suggestions = filterSuggestions.toImmutableList(),
            onSuggestionClick = { suggestion -> viewModel.applySuggestion(suggestion) },
            availableMaxHeightDp = suggestionMaxHeight,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .bottomInsetsPadding(imeAboveNavBars)
                    .padding(bottom = searchBarHeightDp + navBarHeightDp + 8.dp)
                    .padding(horizontal = 8.dp),
        )

        // Floating search bar pill
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .bottomInsetsPadding(imeAboveNavBars)
                    .navigationBarsPadding()
                    .onSizeChanged { size ->
                        searchBarHeightPx = size.height
                    }.padding(bottom = 8.dp)
                    .padding(horizontal = 8.dp),
        ) {
            SearchToolbar(
                state = viewModel.searchFieldState,
            )
        }
    }
}

@Composable
private fun SearchToolbar(state: TextFieldState) {
    val keyboardController = LocalSoftwareKeyboardController.current

    val textFieldColors =
        TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        )

    TextField(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_messages_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.dialog_dismiss),
                    )
                }
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = { keyboardController?.hide() },
        shape = MaterialTheme.shapes.extraLarge,
        colors = textFieldColors,
    )
}

private const val DROPDOWN_FREEZE_MS = 400L
