package com.flxrs.dankchat.preferences.chat.userdisplay

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.twitch.message.Message
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.utils.SwipeToDelete
import com.rarepebble.colorpicker.ColorPickerView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserDisplayScreen(onNavBack: () -> Unit) {
    val viewModel = koinViewModel<UserDisplayViewModel>()
    val events = remember(viewModel) { UserDisplayEventsWrapper(viewModel.events) }
    val userDisplays = viewModel.userDisplays.collectAsStateWithLifecycle().value

    UserDisplayScreen(
        initialItems = userDisplays,
        eventsWrapper = events,
        onSaveAndAddNew = { viewModel.saveChangesAndCreateNew(it) },
        onSaveAndAddItem = { list, new -> viewModel.saveChangesAndAddItem(list, new) },
        onRemoveItem = { viewModel.removeItem(it) },
        onSaveAndNavBack = {
            viewModel.saveChanges(it)
            onNavBack()
        },
    )
}

@Composable
private fun UserDisplayScreen(
    initialItems: ImmutableList<UserDisplayItem>,
    eventsWrapper: UserDisplayEventsWrapper,
    onSaveAndAddNew: (List<UserDisplayItem>) -> Unit,
    onSaveAndAddItem: (List<UserDisplayItem>, UserDisplayItem) -> Unit,
    onRemoveItem: (UserDisplayItem) -> Unit,
    onSaveAndNavBack: (List<UserDisplayItem>) -> Unit,
) {
    val items = remember(initialItems) { initialItems.toMutableStateList() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHost = remember { SnackbarHostState() }
    val removedMessage = stringResource(R.string.item_removed)
    val undo = stringResource(R.string.undo)

    LaunchedEffect(eventsWrapper) {
        eventsWrapper.events.collectLatest {
            when (it) {
                is UserDisplayEvent.ItemRemoved -> {
                    val result = snackbarHost.showSnackbar(
                        message = removedMessage,
                        actionLabel = undo,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onSaveAndAddItem(items, it.removed)
                    }
                }
            }
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
                        onClick = { onSaveAndNavBack(items) },
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
                    onClick = { onSaveAndAddNew(items) },
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { idx, item ->
                val actualItem = rememberUpdatedState(item)
                UserDisplayItem(
                    item = item,
                    onChange = { items[idx] = it },
                    onRemove = { onRemoveItem(actualItem.value) },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .animateItem(),
                )
            }

            if (items.isNotEmpty()) {
                item(key = "save") {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .animateItem(),
                        onClick = { onSaveAndNavBack(items) },
                        content = { Text(stringResource(R.string.save)) },
                    )
                }
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

@Composable
private fun CheckboxWithText(
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(
            selected = checked,
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            onClick = { onCheckedChange(!checked) },
            role = Role.Checkbox,
        )
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            interactionSource = interactionSource,
        )
        Text(
            text = text,
            color = if (enabled) LocalContentColor.current else CheckboxDefaults.colors().disabledBorderColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
