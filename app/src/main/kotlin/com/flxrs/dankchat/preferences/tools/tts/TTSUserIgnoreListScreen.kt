package com.flxrs.dankchat.preferences.tools.tts

import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.preferences.components.NavigationBarSpacer
import com.flxrs.dankchat.utils.compose.SwipeToDelete
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TTSUserIgnoreListScreen(onNavBack: () -> Unit) {
    val viewModel = koinViewModel<TTSUserIgnoreListViewModel>()
    val ignores = viewModel.userIgnores.collectAsStateWithLifecycle().value
    UserIgnoreListScreen(
        initialIgnores = ignores,
        onSaveAndNavBack = {
            viewModel.save(it)
            onNavBack()
        },
        onSave = { viewModel.save(it) },
    )
}

@Composable
private fun UserIgnoreListScreen(
    initialIgnores: ImmutableList<UserIgnore>,
    onSaveAndNavBack: (List<UserIgnore>) -> Unit,
    onSave: (List<UserIgnore>) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val ignores = remember { initialIgnores.toMutableStateList() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMessage = stringResource(R.string.item_removed)
    val undo = stringResource(R.string.undo)

    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            onSave(ignores)
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
                title = { Text(stringResource(R.string.preference_tts_user_ignore_list_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = { onSaveAndNavBack(ignores) },
                        content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) },
                    )
                },
            )
        },
        floatingActionButton = {
            if (!WindowInsets.isImeVisible) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.tts_ignore_list_add_user)) },
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.tts_ignore_list_add_user)) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(8.dp),
                    onClick = { ignores += UserIgnore(user = "") }
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            itemsIndexed(ignores, key = { _, it -> it.id }) { idx, ignore ->
                UserIgnoreItem(
                    user = ignore.user,
                    onUserChanged = { ignores[idx] = ignore.copy(user = it) },
                    onRemove = {
                        focusManager.clearFocus()
                        val removed = ignores.removeAt(idx)
                        scope.launch {
                            snackbarHost.currentSnackbarData?.dismiss()
                            val result = snackbarHost.showSnackbar(
                                message = removedMessage,
                                actionLabel = undo,
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                ignores.add(idx, removed)
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .animateItem(),
                )
            }
            if (ignores.isNotEmpty()) {
                item(key = "save") {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .animateItem(),
                        onClick = { onSaveAndNavBack(ignores) },
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
private fun UserIgnoreItem(
    user: String,
    onUserChanged: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToDelete(onRemove, modifier) {
        ElevatedCard {
            Row {
                OutlinedTextField(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    value = user,
                    onValueChange = onUserChanged,
                    label = { Text(stringResource(R.string.tts_ignore_list_user_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                IconButton(
                    modifier = Modifier.align(Alignment.Top),
                    onClick = onRemove,
                    content = { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.tts_ignore_list_remove_user)) },
                )
            }
        }
    }
}
