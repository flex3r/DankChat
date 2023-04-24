package com.flxrs.dankchat.chat.replies

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.UserName
import com.flxrs.dankchat.databinding.ReplySheetFragmentBinding
import com.flxrs.dankchat.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ReplyInputSheetFragment : Fragment() {

    private val args: ReplyInputSheetFragmentArgs by navArgs()
    private val mainViewModel: MainViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = ReplySheetFragmentBinding.inflate(inflater, container, false).apply {
            replyHeader.text = getString(R.string.reply_header, args.replyUser.value)
        }
        mainViewModel.setReplyingInputSheetState(args.replyMessageId, args.replyUser)
        return binding.root
    }

    companion object {
        fun newInstance(replyMessageId: String, replyUser: UserName) = ReplyInputSheetFragment().apply {
            arguments = ReplyInputSheetFragmentArgs(replyMessageId, replyUser).toBundle()
        }
    }
}
