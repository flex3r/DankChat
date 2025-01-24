package com.flxrs.dankchat.preferences.chat.commands

import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.chat.CustomCommand
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.utils.SwipeToDelete
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CustomCommandsScreen(onNavBack: () -> Unit) {
    val viewModel = koinViewModel<CommandsViewModel>()
    val commands = viewModel.commands.collectAsStateWithLifecycle().value
    CustomCommandsScreen(
        initialCommands = commands,
        onSaveAndNavBack = {
            viewModel.save(it)
            onNavBack()
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomCommandsScreen(initialCommands: ImmutableList<CustomCommand>, onSaveAndNavBack: (List<CustomCommand>) -> Unit) {
    val commands = remember { initialCommands.toMutableStateList() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.item_removed)
    val undo = stringResource(R.string.undo)
    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.commands_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { onSaveAndNavBack(commands) },
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                }
            )
        },
        floatingActionButton = {
            if (!WindowInsets.isImeVisible) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.add_command)) },
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.add_command)) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(8.dp),
                    onClick = { commands += CustomCommand(trigger = "", command = "") },
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
            itemsIndexed(commands, key = { _, it -> it.id }) { idx, command ->
                CustomCommandItem(
                    trigger = command.trigger,
                    command = command.command,
                    onTriggerChanged = { commands[idx] = command.copy(trigger = it) },
                    onCommandChanged = { commands[idx] = command.copy(command = it) },
                    onRemove = {
                        val removed = commands.removeAt(idx)
                        scope.launch {
                            snackbarHost.currentSnackbarData?.dismiss()
                            val result = snackbarHost.showSnackbar(
                                message = removedMessage,
                                actionLabel = undo,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                commands.add(idx, removed)
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .animateItem(),
                )
            }
            if (commands.isNotEmpty()) {
                item(key = "save") {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .animateItem(),
                        onClick = { onSaveAndNavBack(commands) },
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

@Composable
private fun CustomCommandItem(
    trigger: String,
    command: String,
    onTriggerChanged: (String) -> Unit,
    onCommandChanged: (String) -> Unit,
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
                        value = trigger,
                        onValueChange = onTriggerChanged,
                        label = { Text(stringResource(R.string.command_trigger_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = command,
                        onValueChange = onCommandChanged,
                        label = { Text(stringResource(R.string.command__hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }
                IconButton(
                    modifier = Modifier.align(Alignment.Top),
                    onClick = onRemove,
                    content = { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.remove_command)) }
                )
            }
        }
    }
}
