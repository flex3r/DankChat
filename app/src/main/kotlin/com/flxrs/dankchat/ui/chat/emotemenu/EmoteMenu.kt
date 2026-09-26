package com.flxrs.dankchat.ui.chat.emotemenu

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.twitch.emote.GenericEmote
import com.flxrs.dankchat.preferences.components.DankBackground
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoViewModel
import com.flxrs.dankchat.ui.chat.emote.toEmoteSheetData
import com.flxrs.dankchat.ui.main.sheet.EmoteMenuViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmoteMenu(
    onEmoteClick: (String, String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmoteMenuViewModel = koinViewModel(),
    emoteInfoViewModel: EmoteInfoViewModel = koinViewModel(),
) {
    val tabItems by viewModel.emoteTabItems.collectAsStateWithLifecycle()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pagerState =
        rememberPagerState(
            initialPage = selectedTabIndex,
            pageCount = { tabItems.size },
        )

    SideEffect(pagerState.currentPage) {
        viewModel.selectTab(pagerState.currentPage)
    }
    val subsGridState = rememberLazyGridState()
    val subsFirstHeader =
        tabItems
            .getOrNull(EmoteMenuTab.SUBS.ordinal)
            ?.items
            ?.firstOrNull()
            ?.let { (it as? EmoteItem.Header)?.title }

    LaunchedEffect(subsFirstHeader) {
        subsGridState.scrollToItem(0)
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                tabItems.forEachIndexed { index, tabItem ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                text =
                                    when (tabItem.type) {
                                        EmoteMenuTab.RECENT -> stringResource(R.string.emote_menu_tab_recent)
                                        EmoteMenuTab.SUBS -> stringResource(R.string.emote_menu_tab_subs)
                                        EmoteMenuTab.CHANNEL -> stringResource(R.string.emote_menu_tab_channel)
                                        EmoteMenuTab.GLOBAL -> stringResource(R.string.emote_menu_tab_global)
                                    },
                            )
                        },
                    )
                }
            }

            val navBarBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)
            val navBarBottomDp = with(LocalDensity.current) { navBarBottom.toDp() }

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val tab = tabItems[page]
                    EmoteGridPage(
                        tab = tab,
                        subsGridState = subsGridState,
                        navBarBottomDp = navBarBottomDp,
                        onEmoteClick = onEmoteClick,
                        onEmoteLongClick = { emote -> emoteInfoViewModel.show(listOf(emote.toEmoteSheetData())) },
                    )
                }

                // Floating backspace button at bottom-end, matching keyboard position
                IconButton(
                    onClick = onBackspace,
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp + navBarBottomDp)
                            .size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(R.string.backspace),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmoteGridPage(
    tab: EmoteMenuTabItem,
    subsGridState: LazyGridState,
    navBarBottomDp: Dp,
    onEmoteClick: (code: String, id: String) -> Unit,
    onEmoteLongClick: (GenericEmote) -> Unit,
) {
    val items = tab.items

    if (tab.type == EmoteMenuTab.RECENT && items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DankBackground(visible = true)
            Text(
                text = stringResource(R.string.no_recent_emotes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 160.dp),
            )
        }
    } else {
        val gridState =
            when (tab.type) {
                EmoteMenuTab.SUBS -> subsGridState
                else -> rememberLazyGridState()
            }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 40.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 56.dp + navBarBottomDp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = items.size,
                // Sections are unique by title and emote ids are deduplicated per section, so keys
                // stay stable across list shifts and items can be reused
                key = { index ->
                    when (val item = items[index]) {
                        is EmoteItem.Emote -> "emote-${item.emote.emoteType.title}-${item.emote.id}"
                        is EmoteItem.Header -> "header-${item.title}"
                    }
                },
                span = { index ->
                    when (items[index]) {
                        is EmoteItem.Header -> GridItemSpan(maxLineSpan)
                        is EmoteItem.Emote -> GridItemSpan(1)
                    }
                },
                contentType = { index ->
                    when (items[index]) {
                        is EmoteItem.Header -> "header"
                        is EmoteItem.Emote -> "emote"
                    }
                },
            ) { index ->
                val item = items[index]
                when (item) {
                    is EmoteItem.Header -> {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                        )
                    }

                    is EmoteItem.Emote -> {
                        AsyncImage(
                            model = item.emote.url,
                            contentDescription = item.emote.code,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .pointerInput(item.emote) {
                                        detectTapGestures(
                                            onTap = { onEmoteClick(item.emote.code, item.emote.id) },
                                            onLongPress = { onEmoteLongClick(item.emote) },
                                        )
                                    },
                        )
                    }
                }
            }
        }
    }
}
