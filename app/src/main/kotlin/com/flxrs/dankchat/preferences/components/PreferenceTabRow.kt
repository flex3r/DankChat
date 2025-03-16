package com.flxrs.dankchat.preferences.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun PreferenceTabRow(
    appBarContainerColor: State<Color>,
    pagerState: PagerState,
    tabCount: Int,
    tabText: @Composable (Int) -> String,
) {
    val scope = rememberCoroutineScope()
    PrimaryTabRow(
        containerColor = appBarContainerColor.value,
        selectedTabIndex = pagerState.currentPage,
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
