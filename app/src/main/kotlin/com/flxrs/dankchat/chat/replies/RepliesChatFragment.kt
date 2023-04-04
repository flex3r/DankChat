package com.flxrs.dankchat.chat.replies

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.flxrs.dankchat.chat.ChatFragment
import com.flxrs.dankchat.databinding.ChatFragmentBinding
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.showLongSnackbar
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RepliesChatFragment : ChatFragment() {
    private val repliesViewModel: RepliesViewModel by viewModels({ requireParentFragment() })

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

        collectFlow(repliesViewModel.state) {
            when (it) {
                is RepliesState.Found -> adapter.submitList(it.items)
                is RepliesState.NotFound -> {
                    binding.root.showLongSnackbar("Reply thread not found")
                }
            }
        }

        return binding.root
    }
}
