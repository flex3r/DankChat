package com.flxrs.dankchat.preferences.chat.userdisplay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.preferences.components.CheckboxWithText
import com.flxrs.dankchat.preferences.components.DankBackground
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.utils.compose.SwipeToDelete
import com.rarepebble.colorpicker.ColorPickerView
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserDisplayScreen(onNavBack: () -> Unit) {
    val viewModel = koinViewModel<UserDisplayViewModel>()
    val events = remember(viewModel) { UserDisplayEventsWrapper(viewModel.events) }

    LaunchedEffect(Unit) {
        viewModel.fetchUserDisplays()
    }

    UserDisplayScreen(
        userDisplays = viewModel.userDisplays,
        eventsWrapper = events,
        onSave = viewModel::updateUserDisplays,
        onRemove = viewModel::removeUserDisplayItem,
        onAddNew = viewModel::addUserDisplay,
        onAdd = viewModel::addUserDisplayItem,
        onNavBack = onNavBack,
    )
}

@Composable
private fun UserDisplayScreen(
    userDisplays: SnapshotStateList<UserDisplayItem>,
    eventsWrapper: UserDisplayEventsWrapper,
    onSave: (List<UserDisplayItem>) -> Unit,
    onRemove: (UserDisplayItem) -> Unit,
    onAddNew: () -> Unit,
    onAdd: (UserDisplayItem, Int) -> Unit,
    onNavBack: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHost = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(eventsWrapper) {
        eventsWrapper.events.collectLatest {
            focusManager.clearFocus()
            when (it) {
                is UserDisplayEvent.ItemRemoved -> {
                    val result = snackbarHost.showSnackbar(
                        message = context.getString(R.string.item_removed),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onAdd(it.item, it.position)
                    }
                }

                is UserDisplayEvent.ItemAdded   -> {
                    when {
                        it.isLast && listState.canScrollForward -> listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        it.isLast                               -> listState.requestScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        else                                    -> listState.animateScrollToItem(it.position)
                    }
                }
            }
        }
    }

    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            onSave(userDisplays)
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
                title = { Text(stringResource(R.string.custom_user_display_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onSave(userDisplays)
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
        DankBackground(visible = userDisplays.isEmpty())
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            itemsIndexed(userDisplays, key = { _, it -> it.id }) { idx, item ->
                UserDisplayItem(
                    item = item,
                    onChange = { userDisplays[idx] = it },
                    onRemove = { onRemove(userDisplays[idx]) },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                        ),
                )
            }
            item(key = "spacer") {
                NavigationBarSpacer(Modifier.height(112.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserDisplayItem(
    item: UserDisplayItem,
    onChange: (UserDisplayItem) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToDelete(onRemove, modifier) {
        ElevatedCard {
            Row {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = item.username,
                        onValueChange = { onChange(item.copy(username = it)) },
                        label = { Text(stringResource(R.string.username)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        maxLines = 1,
                    )
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        CheckboxWithText(
                            text = stringResource(R.string.enabled),
                            checked = item.enabled,
                            onCheckedChange = { onChange(item.copy(enabled = it)) },
                        )
                        CheckboxWithText(
                            text = stringResource(R.string.user_display_custom_alias_enable),
                            checked = item.aliasEnabled,
                            enabled = item.enabled,
                            onCheckedChange = { onChange(item.copy(aliasEnabled = it)) },
                        )
                        CheckboxWithText(
                            text = stringResource(R.string.user_display_custom_color_enable),
                            checked = item.colorEnabled,
                            enabled = item.enabled,
                            onCheckedChange = { onChange(item.copy(colorEnabled = it)) },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = item.alias,
                            enabled = item.enabled && item.aliasEnabled,
                            onValueChange = { onChange(item.copy(alias = it)) },
                            label = { Text(stringResource(R.string.user_display_alias_input)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(16.dp))
                        var showColorPicker by remember { mutableStateOf(false) }
                        var selectedColor by remember(item.color) { mutableIntStateOf(item.color) }
                        OutlinedButton(
                            onClick = { showColorPicker = true },
                            enabled = item.enabled && item.colorEnabled,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(item.color)),
                            content = { Text(item.formattedDisplayColor) },
                        )
                        if (showColorPicker) {
                            ModalBottomSheet(
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                onDismissRequest = {
                                    onChange(item.copy(color = selectedColor))
                                    showColorPicker = false
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.pick_custom_user_color_title),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                )
                                TextButton(
                                    onClick = { selectedColor = Message.DEFAULT_COLOR },
                                    content = { Text(stringResource(R.string.reset)) },
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(horizontal = 16.dp),
                                )
                                AndroidView(
                                    factory = { context ->
                                        ColorPickerView(context).apply {
                                            showAlpha(false)
                                            setOriginalColor(item.color)
                                            setCurrentColor(selectedColor)
                                            addColorObserver {
                                                selectedColor = it.color
                                            }
                                        }
                                    },
                                    update = {
                                        it.setCurrentColor(selectedColor)
                                    }
                                )
                            }
                        }
                    }
                }
                IconButton(
                    modifier = Modifier.align(Alignment.Top),
                    onClick = onRemove,
                    content = { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.remove_custom_user_display)) }
                )
            }
        }
    }
}


