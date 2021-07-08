package com.flxrs.dankchat.chat.mention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.ChatFragment
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MentionChatFragment : ChatFragment() {
    private val args: MentionChatFragmentArgs by navArgs()
    private val mentionViewModel: MentionViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = ChatFragmentBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = this@MentionChatFragment
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

    override fun onUserClick(targetUserId: String?, targetUserName: String, channel: String, isLongPress: Boolean) {
        targetUserId ?: return
        val shouldLongClickMention = preferences.getBoolean(getString(R.string.preference_user_long_click_key), true)
        val shouldMention = (isLongPress && shouldLongClickMention) || (!isLongPress && !shouldLongClickMention)

        when {
            shouldMention -> (parentFragment as? MainFragment)?.whisperUser(targetUserName)
            else          -> (parentFragment as? MainFragment)?.openUserPopup(targetUserId, channel = null, isWhisperPopup = true)
        }
    }

    companion object {
        fun newInstance(isWhisperTab: Boolean = false) = MentionChatFragment().apply {
            arguments = MentionChatFragmentArgs(isWhisperTab).toBundle()
        }
    }
}