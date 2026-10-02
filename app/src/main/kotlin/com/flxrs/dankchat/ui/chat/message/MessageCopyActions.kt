package com.flxrs.dankchat.ui.chat.message

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import com.flxrs.dankchat.ui.main.MainEvent
import com.flxrs.dankchat.ui.main.MainEventBus
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Immutable
data class MessageCopyActions(
    val copyMessage: (String) -> Unit,
    val copyFullMessage: (String) -> Unit,
)

@Composable
fun rememberMessageCopyActions(): MessageCopyActions {
    val clipboard = LocalClipboard.current
    val mainEventBus: MainEventBus = koinInject()
    val scope = rememberCoroutineScope()

    return remember(clipboard, mainEventBus, scope) {
        fun copy(
            label: String,
            text: String,
        ) {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
                mainEventBus.emitEvent(MainEvent.MessageCopied(text))
            }
        }

        MessageCopyActions(
            copyMessage = { copy("message", it) },
            copyFullMessage = { copy("full message", it) },
        )
    }
}
