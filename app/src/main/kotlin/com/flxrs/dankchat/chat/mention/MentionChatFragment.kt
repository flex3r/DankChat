package com.flxrs.dankchat.chat.mention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.ChatFragment
import com.flxrs.dankchat.data.DisplayName
import com.flxrs.dankchat.data.UserId
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.data.twitch.badge.Badge
import com.flxrs.dankchat.data.twitch.emote.ChatMessageEmote
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MentionChatFragment : ChatFragment() {
    private val args: MentionChatFragmentArgs by navArgs()
    private val mentionViewModel: MentionViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = ChatFragmentBinding.inflate(inflater, container, false).apply {
            chatLayout.layoutTransition?.setAnimateParentHierarchy(false)
            scrollBottom.setOnClickListener {
                scrollBottom.visibility = View.GONE
                isAtBottom = true
                binding.chat.stopScroll()
                super.scrollToPosition(adapter.itemCount - 1)
            }
        }

        when {
            args.isWhisperTab -> collectFlow(mentionViewModel.whispers) { adapter.submitList(it) }
            else              -> collectFlow(mentionViewModel.mentions) { adapter.submitList(it) }
        }

        return binding.root
    }

    override fun onUserClick(
        targetUserId: UserId?,
        targetUserName: UserName,
        targetDisplayName: DisplayName,
        channel: UserName?,
        badges: List<Badge>,
        isLongPress: Boolean
    ) {
        targetUserId ?: return
        val shouldLongClickMention = preferences.getBoolean(getString(R.string.preference_user_long_click_key), true)
        val shouldMention = (isLongPress && shouldLongClickMention) || (!isLongPress && !shouldLongClickMention)

        when {
            shouldMention && dankChatPreferenceStore.isLoggedIn -> (parentFragment?.parentFragment as? MainFragment)?.whisperUser(targetUserName)
            else                                                -> (parentFragment?.parentFragment as? MainFragment)?.openUserPopup(
                targetUserId = targetUserId,
                targetUserName = targetUserName,
                targetDisplayName = targetDisplayName,
                channel = null,
                badges = badges,
                isWhisperPopup = true
            )
        }
    }

    override fun onMessageClick(messageId: String, channel: UserName?, fullMessage: String) {
        (parentFragment?.parentFragment as? MainFragment)?.openMessageSheet(messageId, channel, fullMessage, canReply = false, canModerate = false)
    }

    override fun onEmoteClick(emotes: List<ChatMessageEmote>) {
        (parentFragment?.parentFragment as? MainFragment)?.openEmoteSheet(emotes)
    }

    companion object {
        fun newInstance(isWhisperTab: Boolean = false) = MentionChatFragment().apply {
            arguments = MentionChatFragmentArgs(isWhisperTab).toBundle()
        }
    }
}
