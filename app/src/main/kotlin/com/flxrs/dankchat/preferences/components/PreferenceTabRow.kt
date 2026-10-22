package com.flxrs.dankchat.preferences.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PreferenceTabRow(
    appBarContainerColor: Color,
    pagerState: PagerState,
    tabCount: Int,
    tabText: @Composable (Int) -> String,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints {
        // Equal share so fitting labels fill the row, long ones scroll instead of breaking mid-word
        val minTabWidth = with(LocalDensity.current) { (constraints.maxWidth / tabCount).toDp() }
        PrimaryScrollableTabRow(
            containerColor = appBarContainerColor,
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp,
            minTabWidth = minTabWidth,
        ) {
            val unselectedColor = MaterialTheme.colorScheme.onSurface
            repeat(tabCount) {
                Tab(
                    selected = pagerState.currentPage == it,
                    onClick = {
                        scope.launch {
                            pagerState.scrollToPage(it)
                        }
                    },
                    text = { Text(tabText(it)) },
                    unselectedContentColor = unselectedColor,
                )
            }
        }
    }
}
