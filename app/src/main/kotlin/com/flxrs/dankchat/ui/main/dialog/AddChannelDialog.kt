package com.flxrs.dankchat.ui.main.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.utils.compose.InputBottomSheet

@Composable
fun AddChannelDialog(
    onDismiss: () -> Unit,
    onAddChannels: (List<UserName>) -> Unit,
    isChannelAlreadyAdded: (String) -> Boolean,
) {
    val alreadyAddedError = stringResource(R.string.add_channel_already_added)
    val noChannelsError = stringResource(R.string.add_channels_empty)
    InputBottomSheet(
        title = stringResource(R.string.add_channels),
        hint = stringResource(R.string.add_channels_hint),
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        showClearButton = true,
        singleLine = false,
        validate = { input ->
            val channels = parseChannelNames(input)
            when {
                input.isNotBlank() && channels.isEmpty() -> noChannelsError
                channels.isNotEmpty() && channels.all { isChannelAlreadyAdded(it.value) } -> alreadyAddedError
                else -> null
            }
        },
        onConfirm = { input ->
            val channels = parseChannelNames(input).filterNot { isChannelAlreadyAdded(it.value) }
            onAddChannels(channels)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

internal fun parseChannelNames(input: String): List<UserName> = input
    .split(CHANNEL_NAME_SEPARATOR)
    .filter(String::isNotBlank)
    .map(::UserName)
    .distinctBy { it.value.lowercase() }

private val CHANNEL_NAME_SEPARATOR = "[,\\s]+".toRegex()
