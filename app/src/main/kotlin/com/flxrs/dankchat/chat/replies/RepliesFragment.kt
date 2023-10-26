package com.flxrs.dankchat.chat.replies

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.chat.FullScreenSheetState
import com.flxrs.dankchat.databinding.RepliesFragmentBinding
import com.flxrs.dankchat.main.MainViewModel
import com.flxrs.dankchat.utils.extensions.collectFlow
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RepliesFragment : Fragment() {

    private val args: RepliesFragmentArgs by navArgs()
    private val mainViewModel: MainViewModel by viewModels({ requireParentFragment() })
    private val repliesViewModel: RepliesViewModel by viewModels()

    private var bindingRef: RepliesFragmentBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = RepliesFragmentBinding.inflate(inflater, container, false).apply {
            repliesToolbar.setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
            childFragmentManager.commitNow {
                replace(R.id.replies_chat_fragment, RepliesChatFragment())
            }
        }
        mainViewModel.setFullScreenSheetState(FullScreenSheetState.Replies(args.rootMessageId))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectFlow(repliesViewModel.state) { state ->
            if (state !is RepliesState.Found) return@collectFlow
            val lastMessage = state.items.lastOrNull() ?: return@collectFlow
            mainViewModel.setFullScreenSheetState(FullScreenSheetState.Replies(lastMessage.message.id))
        }
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(rootMessageId: String) = RepliesFragment().apply {
            arguments = RepliesFragmentArgs(rootMessageId).toBundle()
        }
    }
}
