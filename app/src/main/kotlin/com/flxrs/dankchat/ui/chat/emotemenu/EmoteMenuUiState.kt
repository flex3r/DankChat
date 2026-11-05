package com.flxrs.dankchat.ui.chat.emotemenu

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class EmoteMenuUiState(
    val tabItems: ImmutableList<EmoteMenuTabItem>,
    val selectedTabIndex: Int,
    val animateEmotes: Boolean,
)
