package com.flxrs.dankchat.ui.main.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.ui.chat.BadgeUi
import com.flxrs.dankchat.ui.chat.history.HistoryChannel
import com.flxrs.dankchat.ui.chat.user.UserPopupDialog
import com.flxrs.dankchat.ui.chat.user.UserPopupViewModel
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.sheet.FullScreenSheetState
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun UserPopupSheetContainer(onOpenUrl: (String) -> Unit) {
    val userPopupViewModel: UserPopupViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()

    val uiState by userPopupViewModel.state.collectAsStateWithLifecycle()
    val currentState = uiState ?: return

    val currentSheetState by sheetNavigationViewModel.fullScreenSheetState.collectAsStateWithLifecycle()
    val isHistoryOpen = currentSheetState is FullScreenSheetState.History

    val badges = currentState.badges.mapIndexed { index, badge -> BadgeUi(badge.url, badge, index) }.toImmutableList()

    UserPopupDialog(
        state = currentState.popupState,
        badges = badges,
        isOwnUser = currentState.isOwnUser,
        ignoresHighlights = currentState.ignoresHighlights,
        onIgnoreHighlightsChange = userPopupViewModel::setIgnoreHighlights,
        onBlockUser = userPopupViewModel::blockUser,
        onUnblockUser = userPopupViewModel::unblockUser,
        onDismiss = userPopupViewModel::dismiss,
        onMention = when {
            isHistoryOpen -> null

            else -> { name: String, displayName: String ->
                chatInputViewModel.mentionUser(UserName(name), DisplayName(displayName))
            }
        },
        onWhisper = when {
            isHistoryOpen -> null

            else -> { name: String ->
                sheetNavigationViewModel.openWhispers()
                chatInputViewModel.setWhisperTarget(UserName(name))
            }
        },
        onOpenChannel = { userName -> onOpenUrl("https://twitch.tv/$userName") },
        onReport = { userName -> onOpenUrl("https://twitch.tv/$userName/report") },
        onMessageHistory = when {
            isHistoryOpen -> null

            else -> { userName: String ->
                currentState.channel?.let { channel ->
                    sheetNavigationViewModel.openHistory(HistoryChannel.Channel(channel), "from:$userName")
                    userPopupViewModel.dismiss()
                }
            }
        },
        onViewHistory = when {
            isHistoryOpen -> { userName: String ->
                val historyState = currentSheetState as FullScreenSheetState.History
                sheetNavigationViewModel.openHistory(historyState.channel, "from:$userName")
                userPopupViewModel.dismiss()
            }

            else -> null
        },
    )
}
