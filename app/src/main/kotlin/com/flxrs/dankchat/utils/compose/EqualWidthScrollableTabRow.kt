package com.flxrs.dankchat.utils.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Tabs share the row equally while their labels fit, longer labels scroll instead of breaking mid-word. */
@Composable
fun EqualWidthScrollableTabRow(
    selectedTabIndex: Int,
    tabCount: Int,
    containerColor: Color,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = containerColor,
            edgePadding = 0.dp,
            minTabWidth = maxWidth / tabCount.coerceAtLeast(1),
            tabs = tabs,
        )
    }
}
