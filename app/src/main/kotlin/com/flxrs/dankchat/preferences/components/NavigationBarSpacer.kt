package com.flxrs.dankchat.preferences.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NavigationBarSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
}
