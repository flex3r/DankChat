package com.flxrs.dankchat.chat.mention

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import com.flxrs.dankchat.MainFragment
import com.flxrs.dankchat.chat.ChatFragment
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MentionChatFragment : ChatFragment() {
    private val viewModel: MentionViewModel by activityViewModels()

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
            isWhisperTab -> viewModel.whispers.observe(viewLifecycleOwner) { adapter.submitList(it) }
            else -> viewModel.mentions.observe(viewLifecycleOwner) { adapter.submitList(it) }
        }

        return binding.root
    }

//    override fun openUserPopup(user: String) {
//        (parentFragment?.parentFragment as? MainFragment)?.whisperUser(user)
//    } TODO

    companion object {
        fun newInstance(isWhisperTab: Boolean = false): MentionChatFragment {
            return MentionChatFragment().apply {
                arguments = bundleOf(WHISPER_ARG to isWhisperTab)
            }
        }

        const val WHISPER_ARG = "whisper"
    }
}