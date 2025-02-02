package com.flxrs.dankchat.preferences.notifications.ignores

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.CheckboxWithText
import com.flxrs.dankchat.preferences.components.DankBackground
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.preferences.components.PreferenceTabRow
import com.flxrs.dankchat.preferences.notifications.highlights.HighlightsViewModel
import com.flxrs.dankchat.utils.compose.animatedAppBarColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun IgnoresScreen(onNavBack: () -> Unit) {
    val viewModel = koinViewModel<IgnoresViewModel>()
    val events = remember(viewModel) { IgnoreEventsWrapper(viewModel.events) }
    val currentTab = viewModel.currentTab.collectAsStateWithLifecycle().value

    LaunchedEffect(Unit) {
        viewModel.fetchIgnores()
    }

    IgnoresScreen(
        currentTab = currentTab,
        messageIgnores = viewModel.messageIgnores,
        userIgnores = viewModel.userIgnores,
        twitchBlocks = viewModel.twitchBlocks,
        eventsWrapper = events,
        onSave = viewModel::updateIgnores,
        onRemove = viewModel::removeIgnore,
        onAddNew = viewModel::addIgnore,
        onAdd = viewModel::addIgnoreItem,
        onPageChanged = viewModel::setCurrentTab,
        onNavBack = onNavBack,
    )
}

@Composable
private fun IgnoresScreen(
    currentTab: IgnoresTab,
    messageIgnores: SnapshotStateList<MessageIgnoreItem>,
    userIgnores: SnapshotStateList<UserIgnoreItem>,
    twitchBlocks: SnapshotStateList<TwitchBlockItem>,
    eventsWrapper: IgnoreEventsWrapper,
    onSave: (List<MessageIgnoreItem>, List<UserIgnoreItem>) -> Unit,
    onRemove: (IgnoreItem) -> Unit,
    onAddNew: () -> Unit,
    onAdd: (IgnoreItem, Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onNavBack: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHost = remember { SnackbarHostState() }
    val pagerState = rememberPagerState { IgnoresTab.entries.size }
    val listStates = IgnoresTab.entries.map { rememberLazyListState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val appbarContainerColor = animatedAppBarColor(scrollBehavior)

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
                    is IgnoreEvent.ItemRemoved  -> {
                        val message = when (event.item) {
                            is TwitchBlockItem -> context.getString(R.string.unblocked_user, event.item.username)
                            else               -> context.getString(R.string.item_removed)
                        }

                        val result = snackbarHost.showSnackbar(
                            message = message,
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onAdd(event.item, event.position)
                        }
                    }

                    is IgnoreEvent.ItemAdded    -> {
                        val listState = listStates[pagerState.currentPage]
                        when {
                            event.isLast && listState.canScrollForward -> listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            event.isLast                               -> listState.requestScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            else                                       -> listState.animateScrollToItem(event.position)
                        }
                    }

                    is IgnoreEvent.BlockError   -> {
                        val message = context.getString(R.string.blocked_user_failed, event.item.username)
                        snackbarHost.showSnackbar(message)
                    }

                    is IgnoreEvent.UnblockError -> {
                        val message = context.getString(R.string.unblocked_user_failed, event.item.username)
                        snackbarHost.showSnackbar(message)
                    }
                }
            }
    }

    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            onSave(messageIgnores, userIgnores)
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
                title = { Text(stringResource(R.string.ignores)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onSave(messageIgnores, userIgnores)
                            onNavBack()
                        },
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                }
            )
        },
        floatingActionButton = {
            if (!WindowInsets.isImeVisible) {
                AnimatedVisibility(
                    visible = pagerState.currentPage in 0..1,
                    enter = fadeIn() + scaleIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.multi_entry_add_entry)) },
                        icon = { Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.multi_entry_add_entry)) },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(8.dp),
                        onClick = onAddNew,
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .background(color = appbarContainerColor.value)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                val subtitle = when (currentTab) {
                    IgnoresTab.Messages -> stringResource(R.string.ignores_messages_title)
                    IgnoresTab.Users    -> stringResource(R.string.ignores_users_title)
                    IgnoresTab.Twitch   -> stringResource(R.string.ignores_twitch_title)
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
                    appBarContainerColor = appbarContainerColor,
                    pagerState = pagerState,
                    tabCount = IgnoresTab.entries.size,
                    tabText = {
                        when (IgnoresTab.entries[it]) {
                            IgnoresTab.Messages -> stringResource(R.string.tab_messages)
                            IgnoresTab.Users    -> stringResource(R.string.tab_users)
                            IgnoresTab.Twitch   -> stringResource(R.string.tab_twitch)
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
                when (val tab = IgnoresTab.entries[page]) {
                    IgnoresTab.Messages -> IgnoresList(
                        tab = tab,
                        ignores = messageIgnores,
                        listState = listState,
                    ) { idx, item ->
                        MessageIgnoreItem(
                            item = item,
                            onChanged = { messageIgnores[idx] = it },
                            onRemove = { onRemove(item) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                            ),
                        )
                    }

                    IgnoresTab.Users    -> IgnoresList(
                        tab = tab,
                        ignores = userIgnores,
                        listState = listState,
                    ) { idx, item ->
                        UserIgnoreItem(
                            item = item,
                            onChanged = { userIgnores[idx] = it },
                            onRemove = { onRemove(item) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                            ),
                        )
                    }

                    IgnoresTab.Twitch   -> IgnoresList(
                        tab = tab,
                        ignores = twitchBlocks,
                        listState = listState,
                    ) { idx, item ->
                        TwitchBlockItem(
                            item = item,
                            onRemove = { onRemove(item) },
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
private fun <T : IgnoreItem> IgnoresList(
    tab: IgnoresTab,
    ignores: SnapshotStateList<T>,
    listState: LazyListState,
    itemContent: @Composable LazyItemScope.(Int, T) -> Unit,
) {

    DankBackground(visible = ignores.isEmpty())

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "top-spacer") {
            Spacer(Modifier.height(16.dp))
        }
        itemsIndexed(ignores, key = { _, it -> it.id }) { idx, item ->
            itemContent(idx, item)
        }
        item(key = "bottom-spacer") {
            val height = when (tab) {
                IgnoresTab.Messages, IgnoresTab.Users -> 112.dp
                IgnoresTab.Twitch   -> Dp.Unspecified
            }
            NavigationBarSpacer(Modifier.height(height))
        }
    }
}

@Composable
private fun MessageIgnoreItem(
    item: MessageIgnoreItem,
    onChanged: (MessageIgnoreItem) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = LocalUriHandler.current
    val titleText = when (item.type) {
        MessageIgnoreItem.Type.Subscription           -> R.string.highlights_ignores_entry_subscriptions
        MessageIgnoreItem.Type.Announcement           -> R.string.highlights_ignores_entry_announcements
        MessageIgnoreItem.Type.ChannelPointRedemption -> R.string.highlights_ignores_entry_first_messages
        MessageIgnoreItem.Type.FirstMessage           -> R.string.highlights_ignores_entry_elevated_messages
        MessageIgnoreItem.Type.ElevatedMessage        -> R.string.highlights_ignores_entry_redemptions
        MessageIgnoreItem.Type.Custom                 -> R.string.highlights_ignores_entry_custom
    }
    val isCustom = item.type == MessageIgnoreItem.Type.Custom
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
                CheckboxWithText(
                    text = stringResource(R.string.block),
                    checked = item.isBlockMessage,
                    onCheckedChange = { onChanged(item.copy(isBlockMessage = it)) },
                    enabled = item.enabled,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        AnimatedVisibility(visible = isCustom && !item.isBlockMessage) {
            OutlinedTextField(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                value = item.replacement,
                onValueChange = { onChanged(item.copy(replacement = it)) },
                label = { Text(stringResource(R.string.replacement)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun UserIgnoreItem(
    item: UserIgnoreItem,
    onChanged: (UserIgnoreItem) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launcher = LocalUriHandler.current
    ElevatedCard(modifier) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
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
            }
            IconButton(
                onClick = onRemove,
                content = { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear)) },
            )
        }
    }
}

@Composable
private fun TwitchBlockItem(
    item: TwitchBlockItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier) {
        Row {
            val colors = OutlinedTextFieldDefaults.colors()
            OutlinedTextField(
                value = item.username.value,
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp),
                onValueChange = {},
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = colors.unfocusedTextColor,
                    disabledBorderColor = colors.unfocusedIndicatorColor,
                    disabledContainerColor = colors.unfocusedContainerColor,
                ),
                enabled = false,
                maxLines = 1,
            )
            IconButton(
                onClick = onRemove,
                content = { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear)) },
            )
        }
    }
}
