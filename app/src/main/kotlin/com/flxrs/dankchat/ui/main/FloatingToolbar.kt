package com.flxrs.dankchat.ui.main

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledScrollArea
import com.composeunstyled.UnstyledThumb
import com.composeunstyled.UnstyledVerticalScrollbar
import com.composeunstyled.rememberScrollAreaState
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.main.channel.ChannelTabUiState
import com.flxrs.dankchat.ui.main.stream.AudioOnlyBar
import com.flxrs.dankchat.ui.theme.toolbarPillColor
import com.flxrs.dankchat.utils.compose.PagerTabIndicator
import com.flxrs.dankchat.utils.compose.predictiveBackScale
import com.flxrs.dankchat.utils.compose.rememberPagerTabIndicatorState
import com.flxrs.dankchat.utils.compose.reportPosition
import com.flxrs.dankchat.utils.compose.selectedIndicatorBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

@Suppress("MultipleEmitters")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FloatingToolbar(
    tabState: ChannelTabUiState,
    composePagerState: PagerState,
    showAppBar: Boolean,
    isFullscreen: Boolean,
    isLoggedIn: Boolean,
    currentStream: UserName?,
    isAudioOnly: Boolean,
    streamHeightDp: Dp,
    totalMentionCount: Int,
    hasActivePinnedMessage: Boolean,
    isPinnedMessageShown: Boolean,
    channelNotificationsEnabled: Boolean,
    onAction: (ToolbarAction) -> Unit,
    onAudioOnly: () -> Unit,
    onStreamClose: () -> Unit,
    modifier: Modifier = Modifier,
    endAligned: Boolean = false,
    showTabs: Boolean = true,
    addChannelTooltipState: TooltipState? = null,
    onAddChannelTooltipDismiss: () -> Unit = {},
    onSkipTour: () -> Unit = {},
    menuMaxHeightDp: Dp = 0.dp,
    onToolbarBottomChange: (Int) -> Unit = {},
    isEmoteMenuOpen: Boolean = false,
    onCloseEmoteMenu: () -> Unit = {},
    onMenuVisibleChange: (Boolean) -> Unit = {},
    streamToolbarAlpha: () -> Float = { 1f },
) {
    val density = LocalDensity.current
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showQuickSwitch by remember { mutableStateOf(false) }
    var overflowInitialMenu by remember { mutableStateOf<AppBarMenu>(AppBarMenu.Main) }
    var toolbarRowHeight by remember { mutableFloatStateOf(0f) }

    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val toolbarBottomPx = with(density) {
        when {
            isFullscreen -> 0
            !showAppBar -> statusBarTopPx
            else -> statusBarTopPx + (8.dp.toPx() + 16.dp.toPx()).toInt() + toolbarRowHeight.toInt()
        }
    }
    LaunchedEffect(toolbarBottomPx) { onToolbarBottomChange(toolbarBottomPx) }

    val totalTabs = tabState.tabs.size
    val selectedIndex = composePagerState.currentPage
    val tabScrollState = rememberScrollState()
    rememberCoroutineScope()

    val hasOverflow by remember { derivedStateOf { tabScrollState.maxValue > 0 } }

    val tabLayoutState = rememberPagerTabIndicatorState(totalTabs)
    var tabViewportWidth by remember { mutableIntStateOf(0) }

    val keyboardController = LocalSoftwareKeyboardController.current

    // Reset menus when toolbar hides or keyboard opens
    LaunchedEffect(showAppBar) {
        if (!showAppBar) {
            showOverflowMenu = false
            showQuickSwitch = false
        }
    }
    val isKeyboardOpen = WindowInsets.isImeVisible
    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) {
            showOverflowMenu = false
            showQuickSwitch = false
        }
    }
    LaunchedEffect(isEmoteMenuOpen) {
        if (isEmoteMenuOpen) {
            showOverflowMenu = false
            showQuickSwitch = false
        }
    }
    LaunchedEffect(showOverflowMenu, showQuickSwitch) {
        onMenuVisibleChange(showOverflowMenu || showQuickSwitch)
    }

    // Dismiss scrim for menus
    if (showOverflowMenu || showQuickSwitch) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        showOverflowMenu = false
                        showQuickSwitch = false
                        overflowInitialMenu = AppBarMenu.Main
                    },
        )
    }

    val hasStream = currentStream != null && streamHeightDp > 0.dp

    AnimatedVisibility(
        visible = showAppBar && !isFullscreen,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = if (hasStream) streamHeightDp + 8.dp else 0.dp)
                .graphicsLayer { alpha = streamToolbarAlpha() },
    ) {
        val scrimColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        val statusBarPx = statusBarTopPx.toFloat()
        val scrimModifier =
            if (hasStream) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (toolbarRowHeight > 0f) {
                            val gradientHeight = statusBarPx + 8.dp.toPx() + toolbarRowHeight + 16.dp.toPx()
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        0f to scrimColor,
                                        0.75f to scrimColor,
                                        1f to scrimColor.copy(alpha = 0f),
                                        endY = gradientHeight,
                                    ),
                                size = Size(size.width, gradientHeight),
                            )
                        }
                    }.padding(top = with(density) { WindowInsets.statusBars.getTop(density).toDp() } + 8.dp)
            }

        Column(modifier = scrimModifier) {
            if (currentStream != null && isAudioOnly) {
                AudioOnlyBar(
                    channel = currentStream,
                    onExpandVideo = onAudioOnly,
                    onClose = onStreamClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
            }
            Box {
                // Center selected tab when selection changes
                LaunchedEffect(selectedIndex, tabLayoutState.ready, tabViewportWidth) {
                    if (!tabLayoutState.ready || selectedIndex !in tabLayoutState.offsets.indices || tabViewportWidth <= 0) {
                        return@LaunchedEffect
                    }
                    val tabOffset = tabLayoutState.offsets[selectedIndex]
                    val tabWidth = tabLayoutState.widths[selectedIndex]
                    val centeredOffset = tabOffset - (tabViewportWidth / 2 - tabWidth / 2)
                    val clampedOffset = centeredOffset.coerceIn(0, tabScrollState.maxValue)
                    if (tabScrollState.value != clampedOffset) {
                        tabScrollState.animateScrollTo(clampedOffset)
                    }
                }

                // Mention indicators based on scroll position and tab positions
                val hasLeftMention by remember(tabState.tabs) {
                    derivedStateOf {
                        val scrollPos = tabScrollState.value
                        tabState.tabs.indices.any { i ->
                            i < tabLayoutState.offsets.size &&
                                tabLayoutState.offsets[i] + tabLayoutState.widths[i] < scrollPos &&
                                tabState.tabs[i].mentionCount > 0
                        }
                    }
                }
                val hasRightMention by remember(tabState.tabs) {
                    derivedStateOf {
                        val scrollPos = tabScrollState.value
                        tabState.tabs.indices.any { i ->
                            i < tabLayoutState.offsets.size &&
                                tabLayoutState.offsets[i] > scrollPos + tabViewportWidth &&
                                tabState.tabs[i].mentionCount > 0
                        }
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .onSizeChanged {
                                val h = it.height.toFloat()
                                if (toolbarRowHeight == 0f || h < toolbarRowHeight) toolbarRowHeight = h
                            },
                    verticalAlignment = Alignment.Top,
                ) {
                    // Push action pill to end when no tabs are shown
                    if (endAligned && (!showTabs || tabState.tabs.isEmpty())) {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Scrollable tabs pill
                    AnimatedVisibility(
                        visible = showTabs && tabState.tabs.isNotEmpty(),
                        modifier = Modifier.weight(1f, fill = endAligned),
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                    ) {
                        Column(modifier = if (endAligned) Modifier.fillMaxWidth() else Modifier) {
                            val mentionGradientColor = MaterialTheme.colorScheme.error
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.toolbarPillColor,
                                modifier =
                                    Modifier
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .drawWithContent {
                                            drawContent()
                                            val gradientWidth = 24.dp.toPx()
                                            if (hasLeftMention) {
                                                drawRect(
                                                    brush =
                                                        Brush.horizontalGradient(
                                                            colors =
                                                                listOf(
                                                                    mentionGradientColor.copy(alpha = 0.5f),
                                                                    mentionGradientColor.copy(alpha = 0f),
                                                                ),
                                                            endX = gradientWidth,
                                                        ),
                                                    size = Size(gradientWidth, size.height),
                                                )
                                            }
                                            if (hasRightMention) {
                                                drawRect(
                                                    brush =
                                                        Brush.horizontalGradient(
                                                            colors =
                                                                listOf(
                                                                    mentionGradientColor.copy(alpha = 0f),
                                                                    mentionGradientColor.copy(alpha = 0.5f),
                                                                ),
                                                            startX = size.width - gradientWidth,
                                                            endX = size.width,
                                                        ),
                                                    topLeft = Offset(size.width - gradientWidth, 0f),
                                                    size = Size(gradientWidth, size.height),
                                                )
                                            }
                                        },
                            ) {
                                val pillColor = MaterialTheme.colorScheme.toolbarPillColor
                                val indicatorColor = MaterialTheme.colorScheme.primary
                                Box {
                                    Box(
                                        modifier =
                                            Modifier
                                                .padding(horizontal = 12.dp)
                                                .onSizeChanged { tabViewportWidth = it.width }
                                                .clipToBounds()
                                                .horizontalScroll(tabScrollState),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            tabState.tabs.forEachIndexed { index, tab ->
                                                val isSelected = index == selectedIndex
                                                val hasActivity = tab.mentionCount > 0 || tab.hasUnread
                                                val textColor =
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        hasActivity -> MaterialTheme.colorScheme.onSurface
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier =
                                                        Modifier
                                                            .combinedClickable(
                                                                // Let taps fall through to the dismiss scrim while a menu is open
                                                                enabled = !showOverflowMenu && !showQuickSwitch,
                                                                onClick = { onAction(ToolbarAction.SelectTab(index)) },
                                                                onLongClick = { onAction(ToolbarAction.LongClickTab) },
                                                            ).defaultMinSize(minHeight = 48.dp)
                                                            .padding(horizontal = 12.dp, vertical = 14.dp)
                                                            .reportPosition(tabLayoutState, index),
                                                ) {
                                                    Text(
                                                        text = tab.displayName,
                                                        color = textColor,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = when {
                                                            isSelected -> FontWeight.Bold
                                                            hasActivity -> FontWeight.SemiBold
                                                            else -> FontWeight.Normal
                                                        },
                                                    )
                                                    if (tab.mentionCount > 0) {
                                                        Spacer(Modifier.width(4.dp))
                                                        Badge()
                                                    }
                                                }
                                            }
                                            if (hasOverflow) {
                                                Spacer(Modifier.width(18.dp))
                                            }
                                        }

                                        PagerTabIndicator(
                                            pagerState = composePagerState,
                                            state = tabLayoutState,
                                            color = indicatorColor,
                                            modifier = Modifier.align(Alignment.BottomStart),
                                        )
                                    }

                                    // Quick switch dropdown indicator (overlays end of tabs)
                                    if (hasOverflow) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .clickable {
                                                        showOverflowMenu = false
                                                        showQuickSwitch = !showQuickSwitch
                                                        keyboardController?.hide()
                                                        onCloseEmoteMenu()
                                                    }.defaultMinSize(minHeight = 48.dp)
                                                    .padding(start = 4.dp, end = 8.dp)
                                                    .drawBehind {
                                                        val fadeWidth = 12.dp.toPx()
                                                        drawRect(
                                                            brush =
                                                                Brush.horizontalGradient(
                                                                    colors = listOf(pillColor.copy(alpha = 0f), pillColor.copy(alpha = 0.6f)),
                                                                    endX = fadeWidth,
                                                                ),
                                                            size = Size(fadeWidth, size.height),
                                                            topLeft = Offset(-fadeWidth, 0f),
                                                        )
                                                        drawRect(color = pillColor.copy(alpha = 0.6f))
                                                    },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = stringResource(R.string.manage_channels),
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            // Quick switch channel menu
                            AnimatedVisibility(
                                visible = showQuickSwitch,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                                modifier =
                                    Modifier
                                        .padding(top = 4.dp)
                                        .endAlignedOverflow(),
                            ) {
                                var quickSwitchBackProgress by remember { mutableFloatStateOf(0f) }
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.toolbarPillColor,
                                    modifier = Modifier.predictiveBackScale(quickSwitchBackProgress),
                                ) {
                                    PredictiveBackHandler { progress ->
                                        try {
                                            progress.collect { event ->
                                                quickSwitchBackProgress = event.progress
                                            }
                                            showQuickSwitch = false
                                        } catch (_: CancellationException) {
                                            quickSwitchBackProgress = 0f
                                        }
                                    }
                                    val quickSwitchScrollState = rememberScrollState()
                                    val quickSwitchScrollAreaState = rememberScrollAreaState(quickSwitchScrollState)
                                    var itemHeightPx by remember { mutableIntStateOf(0) }
                                    val scrollbarAlpha = remember { Animatable(RESTING_SCROLLBAR_ALPHA) }
                                    LaunchedEffect(Unit) {
                                        val maxScroll = snapshotFlow { quickSwitchScrollState.maxValue }.first { it != Int.MAX_VALUE }
                                        if (maxScroll > 0) {
                                            scrollbarAlpha.snapTo(1f)
                                            delay(400)
                                            scrollbarAlpha.animateTo(RESTING_SCROLLBAR_ALPHA, tween(500))
                                        }
                                    }
                                    UnstyledScrollArea(
                                        state = quickSwitchScrollAreaState,
                                        modifier =
                                            Modifier
                                                .width(IntrinsicSize.Min)
                                                .widthIn(min = 125.dp, max = 200.dp)
                                                .heightIn(max = menuMaxHeightDp),
                                    ) {
                                        // Freeze the displayed selection while the dropdown is closing so the newly
                                        // picked row doesn't flash as selected before the exit animation completes.
                                        var frozenSelection by remember { mutableStateOf<Int?>(null) }
                                        LaunchedEffect(showQuickSwitch) {
                                            if (showQuickSwitch) {
                                                frozenSelection = null
                                            } else if (frozenSelection != null) {
                                                delay(QUICK_SWITCH_FREEZE_MS)
                                                frozenSelection = null
                                            }
                                        }
                                        val displaySelectedIndex = frozenSelection ?: selectedIndex
                                        Column(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .verticalScroll(quickSwitchScrollState)
                                                    .padding(vertical = 8.dp),
                                        ) {
                                            val selectedIndicatorColor = MaterialTheme.colorScheme.primary
                                            tabState.tabs.forEachIndexed { index, tab ->
                                                val isSelected = index == displaySelectedIndex
                                                val hasActivity = tab.mentionCount > 0 || tab.hasUnread
                                                Row(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                frozenSelection = selectedIndex
                                                                onAction(ToolbarAction.SelectTab(index))
                                                                showQuickSwitch = false
                                                            }.selectedIndicatorBar(isSelected, selectedIndicatorColor)
                                                            .defaultMinSize(minHeight = 48.dp)
                                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                                            .then(if (index == 0) Modifier.onSizeChanged { itemHeightPx = it.height } else Modifier),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Start,
                                                ) {
                                                    Text(
                                                        text = tab.displayName,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color =
                                                            when {
                                                                isSelected -> MaterialTheme.colorScheme.primary
                                                                hasActivity -> MaterialTheme.colorScheme.onSurface
                                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                        fontWeight =
                                                            when {
                                                                isSelected -> FontWeight.Bold
                                                                hasActivity -> FontWeight.SemiBold
                                                                else -> FontWeight.Normal
                                                            },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (tab.mentionCount > 0) {
                                                        Spacer(Modifier.width(8.dp))
                                                        Badge()
                                                    }
                                                }
                                            }
                                        }
                                        if (quickSwitchScrollState.maxValue > itemHeightPx) {
                                            UnstyledVerticalScrollbar(
                                                modifier =
                                                    Modifier
                                                        .align(Alignment.TopEnd)
                                                        .fillMaxHeight()
                                                        .padding(vertical = 6.dp)
                                                        .padding(end = 6.dp)
                                                        .width(4.dp),
                                            ) {
                                                UnstyledThumb(
                                                    modifier = Modifier.background(
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = scrollbarAlpha.value),
                                                        RoundedCornerShape(100),
                                                    ),
                                                    enabled = false,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action icons + inline overflow menu
                    val overflowMenuRegistry = remember { InlineMenuItemRegistry() }
                    Row(verticalAlignment = Alignment.Top) {
                        Spacer(Modifier.width(8.dp))

                        CompositionLocalProvider(LocalInlineMenuItemRegistry provides overflowMenuRegistry) {
                            Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.toolbarPillColor,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Reserve space at start when menu is open and not logged in,
                                        // so the pill matches the 3-icon width and icons stay end-aligned
                                        if (!isLoggedIn && showOverflowMenu) {
                                            Spacer(modifier = Modifier.width(48.dp))
                                        }
                                        // Animate the measured width instead of using AnimatedVisibility: the pill
                                        // sits in an IntrinsicSize.Min column, and AnimatedVisibility reports the
                                        // full intrinsic width during its exit, making the tabs pill jump afterwards
                                        val pinButtonWidth by animateDpAsState(
                                            targetValue = if (hasActivePinnedMessage) 48.dp else 0.dp,
                                            label = "pinButtonWidth",
                                        )
                                        if (pinButtonWidth > 0.dp) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier =
                                                    Modifier
                                                        .width(pinButtonWidth)
                                                        .clipToBounds()
                                                        .graphicsLayer { alpha = pinButtonWidth.value / 48f },
                                            ) {
                                                IconButton(onClick = { onAction(ToolbarAction.TogglePinnedMessage) }) {
                                                    when {
                                                        isPinnedMessageShown -> Icon(
                                                            imageVector = Icons.Default.PushPin,
                                                            contentDescription = stringResource(R.string.pinned_message_collapse),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )

                                                        else -> Icon(
                                                            imageVector = Icons.Outlined.PushPin,
                                                            contentDescription = stringResource(R.string.pinned_message_show),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        val addChannelIcon: @Composable () -> Unit = {
                                            IconButton(onClick = { onAction(ToolbarAction.AddChannel) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = stringResource(R.string.add_channel),
                                                )
                                            }
                                        }
                                        if (addChannelTooltipState != null) {
                                            LaunchedEffect(Unit) {
                                                addChannelTooltipState.show()
                                            }
                                            LaunchedEffect(Unit) {
                                                snapshotFlow { addChannelTooltipState.isVisible }
                                                    .dropWhile { !it } // skip initial false
                                                    .first { !it } // wait for dismiss (any cause)
                                                onAddChannelTooltipDismiss()
                                            }
                                            TooltipBox(
                                                positionProvider =
                                                    TooltipDefaults.rememberTooltipPositionProvider(
                                                        TooltipAnchorPosition.Above,
                                                        spacingBetweenTooltipAndAnchor = 8.dp,
                                                    ),
                                                tooltip = {
                                                    val tourColors =
                                                        TooltipDefaults.richTooltipColors(
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            actionContentColor = MaterialTheme.colorScheme.secondary,
                                                        )
                                                    RichTooltip(
                                                        colors = tourColors,
                                                        caretShape = TooltipDefaults.caretShape(caretSize = DpSize(24.dp, 12.dp)),
                                                        action = {
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                TextButton(onClick = {
                                                                    addChannelTooltipState.dismiss()
                                                                    onAddChannelTooltipDismiss()
                                                                    onSkipTour()
                                                                }) {
                                                                    Text(stringResource(R.string.tour_skip))
                                                                }
                                                                TextButton(onClick = {
                                                                    addChannelTooltipState.dismiss()
                                                                    onAddChannelTooltipDismiss()
                                                                }) {
                                                                    Text(stringResource(R.string.tour_next))
                                                                }
                                                            }
                                                        },
                                                    ) {
                                                        Text(stringResource(R.string.tour_add_more_channels_hint))
                                                    }
                                                },
                                                state = addChannelTooltipState,
                                                hasAction = true,
                                            ) {
                                                addChannelIcon()
                                            }
                                        } else {
                                            addChannelIcon()
                                        }
                                        if (isLoggedIn) {
                                            IconButton(onClick = { onAction(ToolbarAction.OpenMentions) }) {
                                                Icon(
                                                    imageVector = when {
                                                        totalMentionCount > 0 -> Icons.Default.Notifications
                                                        else -> Icons.Outlined.Notifications
                                                    },
                                                    contentDescription = stringResource(R.string.mentions_title),
                                                    tint =
                                                        if (totalMentionCount > 0) {
                                                            MaterialTheme.colorScheme.error
                                                        } else {
                                                            LocalContentColor.current
                                                        },
                                                )
                                            }
                                        }
                                        var triggerLayoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(48.dp)
                                                    .onGloballyPositioned { triggerLayoutCoords = it }
                                                    .pointerInput(overflowMenuRegistry) {
                                                        awaitEachGesture {
                                                            awaitFirstDown(requireUnconsumed = false)
                                                            if (showOverflowMenu) {
                                                                showOverflowMenu = false
                                                                return@awaitEachGesture
                                                            }
                                                            showQuickSwitch = false
                                                            overflowInitialMenu = AppBarMenu.Main
                                                            showOverflowMenu = true
                                                            keyboardController?.hide()
                                                            onCloseEmoteMenu()
                                                            while (true) {
                                                                val event = awaitPointerEvent()
                                                                val change = event.changes.first()
                                                                val coords = triggerLayoutCoords
                                                                val windowPos =
                                                                    coords?.localToWindow(change.position) ?: change.position
                                                                overflowMenuRegistry.pressedKey = overflowMenuRegistry.keyAt(windowPos)
                                                                if (!change.pressed) {
                                                                    overflowMenuRegistry.selectAt(windowPos)
                                                                    overflowMenuRegistry.pressedKey = null
                                                                    break
                                                                }
                                                            }
                                                        }
                                                    },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.more),
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(
                                    visible = showOverflowMenu,
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                                    modifier =
                                        Modifier
                                            .skipIntrinsicHeight()
                                            .padding(top = 4.dp)
                                            .endAlignedOverflow(),
                                ) {
                                    InlineOverflowMenu(
                                        isLoggedIn = isLoggedIn,
                                        channelNotificationsEnabled = channelNotificationsEnabled,
                                        onDismiss = {
                                            showOverflowMenu = false
                                            overflowInitialMenu = AppBarMenu.Main
                                        },
                                        initialMenu = overflowInitialMenu,
                                        onAction = onAction,
                                        maxHeightDp = menuMaxHeightDp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Allows the child to measure at its natural width (up to 3x parent width)
 * without affecting the parent Column's width.
 * Reports 0 intrinsic width so [IntrinsicSize.Min] ignores this child.
 * Places the child end-aligned (right edge matches parent right edge).
 */
private fun Modifier.endAlignedOverflow() = this.then(
    object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val parentWidth = constraints.maxWidth
            val placeable =
                measurable.measure(
                    constraints.copy(minWidth = 0, maxWidth = (parentWidth * 3).coerceAtMost(MAX_LAYOUT_SIZE)),
                )
            return layout(parentWidth, placeable.height) {
                placeable.place(parentWidth - placeable.width, 0)
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int,
        ): Int = 0

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int,
        ): Int = 0

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int,
        ): Int = measurable.minIntrinsicHeight(width)

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int,
        ): Int = measurable.maxIntrinsicHeight(width)
    },
)

/**
 * Prevents intrinsic height queries from propagating to children.
 * Needed because [com.composeunstyled.UnstyledScrollArea] crashes on intrinsic height measurement,
 * and [IntrinsicSize.Min] on a parent Column triggers these queries.
 */
private fun Modifier.skipIntrinsicHeight() = this.then(
    object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }

        override fun IntrinsicMeasureScope.minIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int,
        ): Int = 0

        override fun IntrinsicMeasureScope.maxIntrinsicHeight(
            measurable: IntrinsicMeasurable,
            width: Int,
        ): Int = 0

        override fun IntrinsicMeasureScope.minIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int,
        ): Int = measurable.minIntrinsicWidth(height)

        override fun IntrinsicMeasureScope.maxIntrinsicWidth(
            measurable: IntrinsicMeasurable,
            height: Int,
        ): Int = measurable.maxIntrinsicWidth(height)
    },
)

private const val MAX_LAYOUT_SIZE = 16_777_215
private const val QUICK_SWITCH_FREEZE_MS = 400L
