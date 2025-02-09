package com.flxrs.dankchat.preferences.notifications.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.CheckboxWithText
import com.flxrs.dankchat.preferences.components.DankBackground
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceTabRow
import com.flxrs.dankchat.utils.compose.animatedAppBarColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HighlightsScreen(onNavBack: () -> Unit) {
    val viewModel = koinViewModel<HighlightsViewModel>()
    val events = remember(viewModel) { HighlightEventsWrapper(viewModel.events) }
    val currentTab = viewModel.currentTab.collectAsStateWithLifecycle().value

    LaunchedEffect(Unit) {
        viewModel.fetchHighlights()
    }

    HighlightsScreen(
        currentTab = currentTab,
        messageHighlights = viewModel.messageHighlights,
        userHighlights = viewModel.userHighlights,
        blacklistedUsers = viewModel.blacklistedUsers,
        eventsWrapper = events,
        onSave = viewModel::updateHighlights,
        onRemove = viewModel::removeHighlight,
        onAddNew = viewModel::addHighlight,
        onAdd = viewModel::addHighlightItem,
        onPageChanged = viewModel::setCurrentTab,
        onNavBack = onNavBack,
    )
}

@Composable
private fun HighlightsScreen(
    currentTab: HighlightsTab,
    messageHighlights: SnapshotStateList<MessageHighlightItem>,
    userHighlights: SnapshotStateList<UserHighlightItem>,
    blacklistedUsers: SnapshotStateList<BlacklistedUserItem>,
    eventsWrapper: HighlightEventsWrapper,
    onSave: (List<MessageHighlightItem>, List<UserHighlightItem>, List<BlacklistedUserItem>) -> Unit,
    onRemove: (HighlightItem) -> Unit,
    onAddNew: () -> Unit,
    onAdd: (HighlightItem, Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onNavBack: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHost = remember { SnackbarHostState() }
    val pagerState = rememberPagerState { HighlightsTab.entries.size }
    val listStates = HighlightsTab.entries.map { rememberLazyListState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val appBarContainerColor = animatedAppBarColor(scrollBehavior)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            snackbarHost.currentSnackbarData?.dismiss()
            onPageChanged(it)
        }
    }

    LaunchedEffect(eventsWrapper) {
        eventsWrapper.events
            .flowOn(Dispatchers.Main.immediate)
            .collectLatest { event ->
                focusManager.clearFocus()
                when (event) {
                    is HighlightEvent.ItemRemoved -> {
                        val result = snackbarHost.showSnackbar(
                            message = context.getString(R.string.item_removed),
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onAdd(event.item, event.position)
                        }
                    }

                    is HighlightEvent.ItemAdded   -> {
                        val listState = listStates[pagerState.currentPage]
                        when {
                            event.isLast && listState.canScrollForward -> listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            event.isLast                               -> listState.requestScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            else                                       -> listState.animateScrollToItem(event.position)
                        }
                    }
                }
            }
    }

    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            onSave(messageHighlights, userHighlights, blacklistedUsers)
        }
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.highlights)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onSave(messageHighlights, userHighlights, blacklistedUsers)
                            onNavBack()
                        },
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                }
            )
        },
        floatingActionButton = {
            if (!WindowInsets.isImeVisible) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.multi_entry_add_entry)) },
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.multi_entry_add_entry)) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(8.dp),
                    onClick = onAddNew,
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .background(color = appBarContainerColor.value)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                val subtitle = when (currentTab) {
                    HighlightsTab.Messages         -> stringResource(R.string.highlights_messages_title)
                    HighlightsTab.Users            -> stringResource(R.string.highlights_users_title)
                    HighlightsTab.BlacklistedUsers -> stringResource(R.string.highlights_blacklisted_users_title)
                }
                Text(
                    text = subtitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                PreferenceTabRow(
                    appBarContainerColor = appBarContainerColor,
                    pagerState = pagerState,
                    tabCount = HighlightsTab.entries.size,
                    tabText = {
                        when (HighlightsTab.entries[it]) {
                            HighlightsTab.Messages         -> stringResource(R.string.tab_messages)
                            HighlightsTab.Users            -> stringResource(R.string.tab_users)
                            HighlightsTab.BlacklistedUsers -> stringResource(R.string.tab_blacklisted_users)
                        }
                    }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp),
            ) { page ->
                val listState = listStates[page]
                when (HighlightsTab.entries[page]) {
                    HighlightsTab.Messages         -> HighlightsList(
                        highlights = messageHighlights,
                        listState = listState,
                    ) { idx, item ->
                        MessageHighlightItem(
                            item = item,
                            onChanged = { messageHighlights[idx] = it },
                            onRemove = { onRemove(messageHighlights[idx]) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                            ),
                        )
                    }

                    HighlightsTab.Users            -> HighlightsList(
                        highlights = userHighlights,
                        listState = listState,
                    ) { idx, item ->
                        UserHighlightItem(
                            item = item,
                            onChanged = { userHighlights[idx] = it },
                            onRemove = { onRemove(userHighlights[idx]) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                            ),
                        )
                    }

                    HighlightsTab.BlacklistedUsers -> HighlightsList(
                        highlights = blacklistedUsers,
                        listState = listState,
                    ) { idx, item ->
                        BlacklistedUserItem(
                            item = item,
                            onChanged = { blacklistedUsers[idx] = it },
                            onRemove = { onRemove(blacklistedUsers[idx]) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T : HighlightItem> HighlightsList(
    highlights: SnapshotStateList<T>,
    listState: LazyListState,
    itemContent: @Composable LazyItemScope.(Int, T) -> Unit,
) {

    DankBackground(visible = highlights.isEmpty())

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "top-spacer") {
            Spacer(Modifier.height(16.dp))
        }
        itemsIndexed(highlights, key = { _, it -> it.id }) { idx, item ->
            itemContent(idx, item)
        }
        item(key = "bottom-spacer") {
            NavigationBarSpacer(Modifier.height(112.dp))
        }
    }
}

@Composable
private fun MessageHighlightItem(
    item: MessageHighlightItem,
    onChanged: (MessageHighlightItem) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = LocalUriHandler.current
    val titleText = when (item.type) {
        MessageHighlightItem.Type.Username               -> R.string.highlights_entry_username
        MessageHighlightItem.Type.Subscription           -> R.string.highlights_ignores_entry_subscriptions
        MessageHighlightItem.Type.Announcement           -> R.string.highlights_ignores_entry_announcements
        MessageHighlightItem.Type.FirstMessage           -> R.string.highlights_ignores_entry_first_messages
        MessageHighlightItem.Type.ElevatedMessage        -> R.string.highlights_ignores_entry_elevated_messages
        MessageHighlightItem.Type.ChannelPointRedemption -> R.string.highlights_ignores_entry_redemptions
        MessageHighlightItem.Type.Reply                  -> R.string.highlights_ignores_entry_replies
        MessageHighlightItem.Type.Custom                 -> R.string.highlights_ignores_entry_custom
    }
    val isCustom = item.type == MessageHighlightItem.Type.Custom
    ElevatedCard(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(titleText),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
            if (isCustom) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd),
                    content = { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear)) },
                )
            }
        }
        if (isCustom) {
            OutlinedTextField(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                value = item.pattern,
                onValueChange = { onChanged(item.copy(pattern = it)) },
                label = { Text(stringResource(R.string.pattern)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                maxLines = 1,
            )
        }
        FlowRow(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            CheckboxWithText(
                text = stringResource(R.string.enabled),
                checked = item.enabled,
                onCheckedChange = { onChanged(item.copy(enabled = it)) },
                modifier = modifier.padding(end = 8.dp),
            )
            if (isCustom) {
                CheckboxWithText(
                    text = stringResource(R.string.multi_entry_header_regex),
                    checked = item.isRegex,
                    onCheckedChange = { onChanged(item.copy(isRegex = it)) },
                    enabled = item.enabled,
                )
                IconButton(
                    onClick = { launcher.openUri(HighlightsViewModel.REGEX_INFO_URL) },
                    content = { Icon(Icons.Outlined.Info, contentDescription = "regex info") },
                    enabled = item.enabled,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 8.dp),
                )
                CheckboxWithText(
                    text = stringResource(R.string.case_sensitive),
                    checked = item.isCaseSensitive,
                    onCheckedChange = { onChanged(item.copy(isCaseSensitive = it)) },
                    enabled = item.enabled,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (item.canNotify) {
                val enabled = item.notificationsEnabled && (item.type != MessageHighlightItem.Type.Username || item.loggedIn)
                CheckboxWithText(
                    text = stringResource(R.string.create_notification),
                    checked = item.createNotification,
                    onCheckedChange = { onChanged(item.copy(createNotification = it)) },
                    enabled = item.enabled && enabled,
                )
            }
        }
    }
}

@Composable
private fun UserHighlightItem(
    item: UserHighlightItem,
    onChanged: (UserHighlightItem) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = item.username,
                    onValueChange = { onChanged(item.copy(username = it)) },
                    label = { Text(stringResource(R.string.username)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    maxLines = 1,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    CheckboxWithText(
                        text = stringResource(R.string.enabled),
                        checked = item.enabled,
                        onCheckedChange = { onChanged(item.copy(enabled = it)) },
                        modifier = modifier.padding(end = 8.dp),
                    )
                    CheckboxWithText(
                        text = stringResource(R.string.create_notification),
                        checked = item.createNotification,
                        onCheckedChange = { onChanged(item.copy(createNotification = it)) },
                        enabled = item.enabled && item.notificationsEnabled,
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                content = { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear)) },
            )
        }
    }
}

@Composable
private fun BlacklistedUserItem(
    item: BlacklistedUserItem,
    onChanged: (BlacklistedUserItem) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = LocalUriHandler.current
    ElevatedCard(modifier) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = item.username,
                    onValueChange = { onChanged(item.copy(username = it)) },
                    label = { Text(stringResource(R.string.username)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    maxLines = 1,
                )
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    CheckboxWithText(
                        text = stringResource(R.string.enabled),
                        checked = item.enabled,
                        onCheckedChange = { onChanged(item.copy(enabled = it)) },
                        modifier = modifier.padding(end = 8.dp),
                    )
                    CheckboxWithText(
                        text = stringResource(R.string.multi_entry_header_regex),
                        checked = item.isRegex,
                        onCheckedChange = { onChanged(item.copy(isRegex = it)) },
                        enabled = item.enabled,
                    )
                    IconButton(
                        enabled = item.enabled,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        onClick = { launcher.openUri(HighlightsViewModel.REGEX_INFO_URL) },
                        content = { Icon(Icons.Outlined.Info, contentDescription = "regex info") },
                    )
                }
            }
            IconButton(
                onClick = onRemove,
                content = { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear)) },
            )
        }
    }
}
