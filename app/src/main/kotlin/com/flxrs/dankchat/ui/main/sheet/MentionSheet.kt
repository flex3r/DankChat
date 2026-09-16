package com.flxrs.dankchat.ui.main.sheet

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.ScrollDirectionTracker
import com.flxrs.dankchat.ui.chat.mention.MentionComposable
import com.flxrs.dankchat.ui.chat.mention.MentionViewModel
import com.flxrs.dankchat.utils.compose.PagerTabIndicator
import com.flxrs.dankchat.utils.compose.rememberPagerTabIndicatorState
import com.flxrs.dankchat.utils.compose.reportPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MentionSheet(
    mentionViewModel: MentionViewModel,
    initialisWhisperTab: Boolean,
    onDismiss: () -> Unit,
    onWhisperReply: ((userName: UserName) -> Unit)? = null,
    bottomContentPadding: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pagerState =
        rememberPagerState(
            initialPage = if (initialisWhisperTab) 1 else 0,
            pageCount = { 2 },
        )
    var backProgress by remember { mutableFloatStateOf(0f) }
    var toolbarVisible by remember { mutableStateOf(true) }

    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val toolbarTopPadding = statusBarHeight + 8.dp + 48.dp + 16.dp
    val sheetBackgroundColor =
        lerp(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            fraction = 0.75f,
        )

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

    val whisperMentionCount by mentionViewModel.whisperMentionCount.collectAsStateWithLifecycle()

    SideEffect(pagerState.currentPage) {
        mentionViewModel.setCurrentTab(pagerState.currentPage)
        when (pagerState.currentPage) {
            0 -> mentionViewModel.markMentionsRead()

            1 -> {
                mentionViewModel.clearWhisperMentionCount()
                mentionViewModel.markWhispersRead()
            }
        }
    }

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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            @Suppress("ViewModelForwarding")
            MentionComposable(
                mentionViewModel = mentionViewModel,
                isWhisperTab = page == 1,
                onWhisperReply = if (page == 1) onWhisperReply else null,
                containerColor = sheetBackgroundColor,
                contentPadding = PaddingValues(top = toolbarTopPadding, bottom = bottomContentPadding),
                scrollModifier = scrollModifier,
                onScrollToBottom = { toolbarVisible = true },
            )
        }

        SheetToolbar(
            visible = toolbarVisible,
            statusBarHeight = statusBarHeight,
            sheetBackgroundColor = sheetBackgroundColor,
            onBack = onDismiss,
        ) {
            // Falls back to icons once the labels no longer fit, so both tabs stay visible in a narrow pane
            BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
                val tabs = listOf(R.string.mentions to Icons.Default.AlternateEmail, R.string.whispers to Icons.AutoMirrored.Filled.Chat)
                val tabStyle = MaterialTheme.typography.titleSmall
                val labels = tabs.map { (stringRes, _) -> stringResource(stringRes) }
                val textMeasurer = rememberTextMeasurer()
                val textTabsWidth =
                    remember(labels, tabStyle, density) {
                        val labelsWidth = with(density) { labels.sumOf { textMeasurer.measure(it, tabStyle).size.width }.toDp() }
                        labelsWidth + TAB_HORIZONTAL_PADDING * 2 * tabs.size + TAB_BADGE_ALLOWANCE
                    }
                val useIcons = textTabsWidth > maxWidth
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    val tabLayoutState = rememberPagerTabIndicatorState(tabs.size)
                    val indicatorColor = MaterialTheme.colorScheme.primary
                    Box {
                        Row {
                            tabs.forEachIndexed { index, (_, icon) ->
                                val isSelected = pagerState.currentPage == index
                                val textColor =
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                                            .defaultMinSize(minHeight = 48.dp)
                                            .padding(horizontal = TAB_HORIZONTAL_PADDING, vertical = 14.dp)
                                            .reportPosition(tabLayoutState, index),
                                ) {
                                    when {
                                        useIcons -> {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = labels[index],
                                                tint = textColor,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }

                                        else -> {
                                            Text(
                                                text = labels[index],
                                                color = textColor,
                                                style = tabStyle,
                                            )
                                        }
                                    }
                                    if (index == 1 && whisperMentionCount > 0 && !isSelected) {
                                        Spacer(Modifier.width(4.dp))
                                        Badge()
                                    }
                                }
                            }
                        }
                        PagerTabIndicator(
                            pagerState = pagerState,
                            state = tabLayoutState,
                            color = indicatorColor,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.SheetToolbar(
    visible: Boolean,
    statusBarHeight: Dp,
    sheetBackgroundColor: Color,
    onBack: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
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

                content()
            }
        }
    }

    if (!visible) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(sheetBackgroundColor.copy(alpha = 0.7f)),
        )
    }
}

private val TAB_HORIZONTAL_PADDING = 16.dp
private val TAB_BADGE_ALLOWANCE = 12.dp
