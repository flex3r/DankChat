package com.flxrs.dankchat.ui.main.dialog

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flxrs.dankchat.ui.chat.emote.EmoteInfoViewModel
import com.flxrs.dankchat.ui.main.input.ChatInputViewModel
import com.flxrs.dankchat.ui.main.sheet.FullScreenSheetState
import com.flxrs.dankchat.ui.main.sheet.SheetNavigationViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EmoteInfoSheetContainer(
    isLoggedIn: Boolean,
    onOpenUrl: (String) -> Unit,
) {
    val emoteInfoViewModel: EmoteInfoViewModel = koinViewModel()
    val chatInputViewModel: ChatInputViewModel = koinViewModel()
    val sheetNavigationViewModel: SheetNavigationViewModel = koinViewModel()

    val items by emoteInfoViewModel.items.collectAsStateWithLifecycle()
    if (items.isEmpty()) return

    val sheetState by sheetNavigationViewModel.fullScreenSheetState.collectAsStateWithLifecycle()
    val whisperTarget by chatInputViewModel.whisperTarget.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val canUseEmote =
        isLoggedIn &&
            when (sheetState) {
                is FullScreenSheetState.Closed,
                is FullScreenSheetState.Replies,
                -> true

                is FullScreenSheetState.Mention,
                is FullScreenSheetState.Whisper,
                -> whisperTarget != null

                is FullScreenSheetState.History -> false
            }

    // Pager and page height state are sized for one set of items
    key(items) {
        EmoteInfoDialog(
            items = items,
            isLoggedIn = canUseEmote,
            onUseEmote = { chatInputViewModel.insertEmote(it) },
            onCopyEmote = {
                scope.launch {
                    clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("emote", it)))
                }
            },
            onOpenLink = onOpenUrl,
            onDismiss = emoteInfoViewModel::dismiss,
        )
    }
}
