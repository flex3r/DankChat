package com.flxrs.dankchat.chat.emote

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmoteType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EmoteSheetViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val args = EmoteSheetFragmentArgs.fromSavedStateHandle(savedStateHandle)

    val items = args.emotes.map { emote ->
        EmoteSheetItem(
            id = emote.id,
            name = emote.code,
            imageUrl = emote.url,
            baseName = emote.baseNameOrNull(),
            creatorName = emote.creatorNameOrNull(),
            providerUrl = emote.providerUrlOrNull(),
            isZeroWidth = emote.isOverlayEmote,
            emoteType = emote.emoteTypeOrNull(),
        )
    }

    private fun ChatMessageEmote.baseNameOrNull(): String? {
        return when (type) {
            is ChatMessageEmoteType.GlobalSevenTVEmote  -> type.baseName
            is ChatMessageEmoteType.ChannelSevenTVEmote -> type.baseName
            else                                        -> null
        }
    }

    private fun ChatMessageEmote.creatorNameOrNull(): DisplayName? {
        return when (type) {
            is ChatMessageEmoteType.GlobalSevenTVEmote  -> type.creator
            is ChatMessageEmoteType.ChannelSevenTVEmote -> type.creator
            is ChatMessageEmoteType.ChannelBTTVEmote    -> type.creator
            is ChatMessageEmoteType.ChannelFFZEmote     -> type.creator
            is ChatMessageEmoteType.GlobalFFZEmote      -> type.creator
            else                                        -> null
        }
    }

    private fun ChatMessageEmote.providerUrlOrNull(): String {
        return when (type) {
            is ChatMessageEmoteType.GlobalSevenTVEmote,
            is ChatMessageEmoteType.ChannelSevenTVEmote -> "$SEVEN_TV_BASE_LINK$id"

            is ChatMessageEmoteType.ChannelBTTVEmote,
            is ChatMessageEmoteType.GlobalBTTVEmote     -> "$BTTV_BASE_LINK$id"

            is ChatMessageEmoteType.ChannelFFZEmote,
            is ChatMessageEmoteType.GlobalFFZEmote      -> "$FFZ_BASE_LINK$id-$code"

            is ChatMessageEmoteType.TwitchEmote         -> "$RACCATTACK_BASE_LINK$id"
        }
    }

    private fun ChatMessageEmote.emoteTypeOrNull(): Int {
        return when (type) {
            is ChatMessageEmoteType.ChannelBTTVEmote    -> if (type.isShared) R.string.emote_sheet_bttv_shared_emote else R.string.emote_sheet_bttv_channel_emote
            is ChatMessageEmoteType.ChannelFFZEmote     -> R.string.emote_sheet_ffz_channel_emote
            is ChatMessageEmoteType.ChannelSevenTVEmote -> R.string.emote_sheet_seventv_channel_emote
            ChatMessageEmoteType.GlobalBTTVEmote        -> R.string.emote_sheet_bttv_global_emote
            is ChatMessageEmoteType.GlobalFFZEmote      -> R.string.emote_sheet_ffz_global_emote
            is ChatMessageEmoteType.GlobalSevenTVEmote  -> R.string.emote_sheet_seventv_global_emote
            ChatMessageEmoteType.TwitchEmote            -> R.string.emote_sheet_twitch_emote
        }
    }

    companion object {
        private const val SEVEN_TV_BASE_LINK = "https://7tv.app/emotes/"
        private const val FFZ_BASE_LINK = "https://www.frankerfacez.com/emoticon/"
        private const val BTTV_BASE_LINK = "https://betterttv.com/emotes/"
        private const val RACCATTACK_BASE_LINK = "https://emotes.raccatta.cc/twitch/emote/"
    }
}
