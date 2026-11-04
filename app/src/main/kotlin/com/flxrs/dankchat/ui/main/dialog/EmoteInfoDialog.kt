package com.flxrs.dankchat.ui.main.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.flxrs.dankchat.R
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoItem
import com.flxrs.dankchat.utils.compose.rememberModalSheetState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmoteInfoDialog(
    items: ImmutableList<EmoteInfoItem>,
    isLoggedIn: Boolean,
    onUseEmote: (String) -> Unit,
    onCopyEmote: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { items.size })

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            if (items.size > 1) {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    items.forEachIndexed { index, item ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(item.name) },
                        )
                    }
                }
            }

            val density = LocalDensity.current
            val pageHeights = remember { IntArray(items.size) }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = items.size,
                overscrollEffect = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .interpolatedHeight(pagerState, pageHeights, density),
            ) { page ->
                val item = items[page]
                EmoteInfoContent(
                    item = item,
                    showUseEmote = isLoggedIn,
                    onUseEmote = {
                        onUseEmote(item.name)
                        onDismiss()
                    },
                    onCopyEmote = {
                        onCopyEmote(item.name)
                        onDismiss()
                    },
                    onOpenLink = {
                        onOpenLink(item.providerUrl)
                        onDismiss()
                    },
                    modifier =
                        Modifier
                            .wrapContentHeight(unbounded = true)
                            .onSizeChanged { pageHeights[page] = it.height },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun Modifier.interpolatedHeight(
    pagerState: PagerState,
    pageHeights: IntArray,
    density: Density,
): Modifier {
    val currentHeight = pageHeights.getOrNull(pagerState.currentPage) ?: 0
    if (currentHeight == 0) return this

    val fraction = pagerState.currentPageOffsetFraction
    val targetPage = when {
        fraction > 0 -> (pagerState.currentPage + 1).coerceAtMost(pagerState.pageCount - 1)
        fraction < 0 -> (pagerState.currentPage - 1).coerceAtLeast(0)
        else -> pagerState.currentPage
    }
    val targetHeight = pageHeights.getOrNull(targetPage) ?: currentHeight
    val interpolatedPx = lerp(currentHeight, targetHeight, fraction.absoluteValue)
    val heightDp = with(density) { interpolatedPx.toDp() }
    return then(Modifier.height(heightDp))
}

private const val WIDE_EMOTE_THRESHOLD = 1.5f

@Composable
private fun EmoteInfoContent(
    item: EmoteInfoItem,
    showUseEmote: Boolean,
    onUseEmote: () -> Unit,
    onCopyEmote: () -> Unit,
    onOpenLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var aspectRatio by remember { mutableFloatStateOf(1f) }
    val isWide = aspectRatio > WIDE_EMOTE_THRESHOLD

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isWide) {
            WideEmoteHeader(item = item, onAspectRatio = { aspectRatio = it })
        } else {
            SquareEmoteHeader(item = item, onAspectRatio = { aspectRatio = it })
        }

        if (showUseEmote) {
            ListItem(
                leadingContent = { Icon(Icons.Default.InsertEmoticon, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onUseEmote),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) {
                Text(stringResource(R.string.emote_sheet_use))
            }
        }
        ListItem(
            leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onCopyEmote),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(stringResource(R.string.emote_sheet_copy))
        }
        ListItem(
            leadingContent = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenLink),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(stringResource(R.string.emote_sheet_open_link))
        }
    }
}

@Composable
private fun SquareEmoteHeader(
    item: EmoteInfoItem,
    onAspectRatio: (Float) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = stringResource(R.string.emote_sheet_image_description),
            modifier = Modifier.size(96.dp),
            onSuccess = { state ->
                val size = state.result.image
                if (size.height > 0) {
                    onAspectRatio(size.width.toFloat() / size.height)
                }
            },
        )
        Spacer(modifier = Modifier.width(16.dp))
        EmoteInfoText(item = item, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WideEmoteHeader(
    item: EmoteInfoItem,
    onAspectRatio: (Float) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmoteInfoText(item = item)
        Spacer(modifier = Modifier.height(12.dp))
        AsyncImage(
            model = item.imageUrl,
            contentDescription = stringResource(R.string.emote_sheet_image_description),
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .heightIn(max = 96.dp)
                    .fillMaxWidth(0.5f),
            onSuccess = { state ->
                val size = state.result.image
                if (size.height > 0) {
                    onAspectRatio(size.width.toFloat() / size.height)
                }
            },
        )
    }
}

@Composable
private fun EmoteInfoText(
    item: EmoteInfoItem,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(item.emoteType),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        item.baseName?.let {
            Text(
                text = stringResource(R.string.emote_sheet_alias_of, it),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        item.creatorName?.let {
            Text(
                text = stringResource(R.string.emote_sheet_created_by, it),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = if (item.isZeroWidth) stringResource(R.string.emote_sheet_zero_width_emote) else "",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}
