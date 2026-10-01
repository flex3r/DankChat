package com.flxrs.dankchat.ui.main.sheet

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flxrs.dankchat.R
import com.flxrs.dankchat.ui.chat.ScrollDirectionTracker
import com.flxrs.dankchat.ui.chat.replies.RepliesComposable
import com.flxrs.dankchat.ui.chat.replies.RepliesViewModel
import kotlinx.coroutines.CancellationException
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RepliesSheet(
    rootMessageId: String,
    onDismiss: () -> Unit,
    bottomContentPadding: Dp = 0.dp,
) {
    val viewModel: RepliesViewModel =
        koinViewModel(
            key = rootMessageId,
            parameters = { parametersOf(rootMessageId) },
        )
    val density = LocalDensity.current
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
        RepliesComposable(
            repliesViewModel = viewModel,
            onMissing = onDismiss,
            containerColor = sheetBackgroundColor,
            contentPadding = PaddingValues(top = toolbarTopPadding, bottom = bottomContentPadding),
            scrollModifier = scrollModifier,
            onScrollToBottom = { toolbarVisible = true },
            modifier = Modifier.fillMaxSize(),
        )

        SheetToolbar(
            visible = toolbarVisible,
            statusBarHeight = statusBarHeight,
            sheetBackgroundColor = sheetBackgroundColor,
            onBack = onDismiss,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    text = stringResource(R.string.replies_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
