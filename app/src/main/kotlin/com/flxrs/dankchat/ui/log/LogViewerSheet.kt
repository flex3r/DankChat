package com.flxrs.dankchat.ui.log

import android.content.ClipData
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.repo.log.LogLevel
import com.flxrs.dankchat.data.repo.log.LogLine
import com.flxrs.dankchat.ui.chat.ScrollDirectionTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LogViewerSheet(onDismiss: () -> Unit) {
    val viewModel: LogViewerViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedIndices by viewModel.selectedIndices.collectAsStateWithLifecycle()
    val isSelectionActive by remember { derivedStateOf { selectedIndices.isNotEmpty() } }
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val sheetBackgroundColor =
        lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            fraction = 0.75f,
        )

    var backProgress by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

    val ime = WindowInsets.ime
    val navBars = WindowInsets.navigationBars
    val navBarHeightDp = with(density) { navBars.getBottom(density).toDp() }
    val currentImeHeight = (ime.getBottom(density) - navBars.getBottom(density)).coerceAtLeast(0)
    val currentImeDp = with(density) { currentImeHeight.toDp() }

    var searchBarHeightPx by remember { mutableIntStateOf(0) }
    val searchBarHeightDp = with(density) { searchBarHeightPx.toDp() }

    val toolbarTopPadding = statusBarHeight + 8.dp + 48.dp + 8.dp + 32.dp + 16.dp

    PredictiveBackHandler { progress ->
        try {
            progress.collect { event ->
                backProgress = event.progress
            }
            when {
                isSelectionActive -> viewModel.clearSelection()
                else -> onDismiss()
            }
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

    val listState = rememberLazyListState()
    var shouldAutoScroll by rememberSaveable { mutableStateOf(true) }
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    SideEffect(listState.isScrollInProgress) {
        if (listState.lastScrolledForward && shouldAutoScroll) {
            shouldAutoScroll = false
        }
        if (!listState.isScrollInProgress && isAtBottom && !shouldAutoScroll) {
            shouldAutoScroll = true
        }
    }

    LaunchedEffect(shouldAutoScroll, state.lines.size) {
        if (shouldAutoScroll && state.lines.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(sheetBackgroundColor)
                .graphicsLayer {
                    val scale = 1f - (backProgress * 0.1f)
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - backProgress
                    translationY = backProgress * 100f
                },
    ) {
        LazyColumnScrollbar(
            state = listState,
            settings = ScrollbarSettings(
                thumbUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                thumbSelectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                thumbThickness = 8.dp,
                thumbMinLength = 0.1f,
                thumbShape = RoundedCornerShape(100),
            ),
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(
                    top = toolbarTopPadding,
                    bottom = searchBarHeightDp + navBarHeightDp + currentImeDp,
                ),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollTracker),
            ) {
                itemsIndexed(
                    items = state.lines,
                    key = { index, _ -> index },
                ) { index, line ->
                    LogLineItem(
                        line = line,
                        isSelected = index in selectedIndices,
                        onClick = { viewModel.toggleSelection(index) },
                    )
                }
            }
        }

        // Scroll-to-bottom FAB
        AnimatedVisibility(
            visible = !shouldAutoScroll && state.lines.isNotEmpty(),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = searchBarHeightDp + navBarHeightDp + currentImeDp + 8.dp),
        ) {
            FloatingActionButton(
                onClick = {
                    shouldAutoScroll = true
                    toolbarVisible = true
                },
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.scroll_to_bottom),
                )
            }
        }

        // Floating toolbar
        LogViewerToolbar(
            visible = toolbarVisible,
            statusBarHeight = statusBarHeight,
            sheetBackgroundColor = sheetBackgroundColor,
            levelFilter = state.levelFilter,
            isSelectionActive = isSelectionActive,
            selectedCount = selectedIndices.size,
            onBack = onDismiss,
            onLevelFilter = viewModel::setLevelFilter,
            onShare = {
                val file = viewModel.getShareableLogFile() ?: return@LogViewerToolbar
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, null))
            },
            onClearSelection = viewModel::clearSelection,
            onCopySelection = {
                val text = viewModel.getSelectedLinesText()
                scope.launch {
                    clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("log_lines", text)))
                }
                viewModel.clearSelection()
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Status bar fill when toolbar hidden
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

        // Search bar
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = currentImeDp)
                    .navigationBarsPadding()
                    .onSizeChanged { searchBarHeightPx = it.height }
                    .padding(bottom = 8.dp)
                    .padding(horizontal = 8.dp),
        ) {
            LogSearchToolbar(
                state = viewModel.searchFieldState,
                enabled = !isSelectionActive,
            )
        }
    }
}

@Composable
private fun LogViewerToolbar(
    visible: Boolean,
    statusBarHeight: Dp,
    sheetBackgroundColor: Color,
    levelFilter: LogLevel?,
    isSelectionActive: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onLevelFilter: (LogLevel) -> Unit,
    onShare: () -> Unit,
    onClearSelection: () -> Unit,
    onCopySelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                0f to sheetBackgroundColor.copy(alpha = 0.7f),
                                0.75f to sheetBackgroundColor.copy(alpha = 0.7f),
                                1f to sheetBackgroundColor.copy(alpha = 0f),
                            ),
                    ).padding(top = statusBarHeight + 8.dp)
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedContent(
                    targetState = isSelectionActive,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { -it / 4 })
                            .togetherWith(fadeOut() + slideOutVertically { -it / 4 })
                    },
                    label = "ToolbarModeTransition",
                ) { selectionMode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            selectionMode -> {
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    IconButton(onClick = onClearSelection) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.log_viewer_clear_selection),
                                        )
                                    }
                                }

                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Text(
                                        text = pluralStringResource(R.plurals.log_viewer_selected_count, selectedCount, selectedCount),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }

                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    IconButton(onClick = onCopySelection) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.log_viewer_copy_selection),
                                        )
                                    }
                                }
                            }

                            else -> {
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    IconButton(onClick = onBack) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.back),
                                        )
                                    }
                                }

                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Text(
                                        text = stringResource(R.string.log_viewer_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    )
                                }

                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    IconButton(onClick = onShare) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = stringResource(R.string.log_viewer_share),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Level filter chips
                AnimatedVisibility(visible = !isSelectionActive) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LogLevel.entries.forEach { level ->
                            FilterChip(
                                selected = levelFilter == level,
                                onClick = { onLevelFilter(level) },
                                label = { Text(level.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLineItem(
    line: LogLine,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    Text(
        text = formatLogLine(line),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun formatLogLine(line: LogLine): AnnotatedString {
    if (line.timestamp.isEmpty()) {
        return AnnotatedString(line.raw)
    }

    val timestampColor = MaterialTheme.colorScheme.outline
    val threadColor = MaterialTheme.colorScheme.tertiary
    val loggerColor = MaterialTheme.colorScheme.secondary
    val messageColor = MaterialTheme.colorScheme.onSurface
    val levelColor = when (line.level) {
        LogLevel.TRACE -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurface
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> Color(0xFFFFA000)
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }

    return buildAnnotatedString {
        withStyle(SpanStyle(color = timestampColor)) {
            append(line.timestamp)
        }
        append(' ')
        withStyle(SpanStyle(color = threadColor)) {
            append('[')
            append(line.thread)
            append(']')
        }
        append(' ')
        withStyle(SpanStyle(color = levelColor, fontWeight = FontWeight.Bold)) {
            append(line.level.name.padEnd(5))
        }
        append(' ')
        withStyle(SpanStyle(color = loggerColor)) {
            append(line.logger)
        }
        append(" - ")
        withStyle(SpanStyle(color = messageColor)) {
            append(line.message)
        }
    }
}

@Composable
private fun LogSearchToolbar(
    state: TextFieldState,
    enabled: Boolean = true,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFieldColors =
        TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )

    TextField(
        state = state,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.log_viewer_search_hint)) },
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
