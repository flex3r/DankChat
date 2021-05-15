package com.flxrs.dankchat.chat.mention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import com.flxrs.dankchat.chat.ChatFragment
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MentionChatFragment : ChatFragment() {
    private val mentionViewModel: MentionViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val isWhisperTab = requireArguments().getBoolean(WHISPER_ARG, false)
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
            isWhisperTab -> collectFlow(mentionViewModel.whispers) { adapter.submitList(it) }
            else -> collectFlow(mentionViewModel.mentions) { adapter.submitList(it) }
        }

        return binding.root
    }

    override fun openUserPopup(targetUserId: String?) {
        targetUserId ?: return
        (parentFragment?.parentFragment as? MainFragment)?.openUserPopup(targetUserId, isWhisperPopup = true)
    }

    companion object {
        fun newInstance(isWhisperTab: Boolean = false): MentionChatFragment {
            return MentionChatFragment().apply {
                arguments = bundleOf(WHISPER_ARG to isWhisperTab)
            }
        }

        const val WHISPER_ARG = "whisper"
    }
}