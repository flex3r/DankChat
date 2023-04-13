package com.flxrs.dankchat.chat.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MessageBottomsheetBinding
import com.flxrs.dankchat.databinding.TimeoutDialogBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.collectFlow
import com.flxrs.dankchat.utils.extensions.isLandscape
import com.flxrs.dankchat.utils.extensions.showShortSnackbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MessageSheetFragment : BottomSheetDialogFragment() {

    private val args: MessageSheetFragmentArgs by navArgs()
    private val viewModel: MessageSheetViewModel by viewModels()
    private var bindingRef: MessageBottomsheetBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = MessageBottomsheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        collectFlow(viewModel.state) { state ->
            when (state) {
                MessageSheetState.Default  -> Unit
                MessageSheetState.NotFound -> binding.root.showShortSnackbar(getString(R.string.message_not_found))
                is MessageSheetState.Found -> with(binding) {
                    moderationGroup.isVisible = state.canModerate
                    messageViewThread.isVisible = state.hasReplyThread
                    messageReply.isVisible = state.canReply
                    if (state.canModerate) {
                        userTimeout.setOnClickListener { showTimeoutDialog() }
                        userDelete.setOnClickListener { showDeleteDialog() }
                        userBan.setOnClickListener { showBanDialog() }
                        userUnban.setOnClickListener {
                            lifecycleScope.launch {
                                viewModel.unbanUser()
                                dialog?.dismiss()
                            }
                        }
                    }
                    if (state.canReply) {
                        messageReply.setOnClickListener { sendResultAndDismiss(MessageSheetResult.Reply(state.replyMessageId, state.replyName)) }
                    }
                    if (state.hasReplyThread) {
                        messageViewThread.setOnClickListener { sendResultAndDismiss(MessageSheetResult.ViewThread(state.replyMessageId)) }
                    }
                    messageCopy.setOnClickListener { sendResultAndDismiss(MessageSheetResult.Copy(state.originalMessage)) }
                    messageMoreActions.setOnClickListener { sendResultAndDismiss(MessageSheetResult.OpenMoreActions(args.messageId, args.fullMessage)) }
                }
            }

        }
    }

    override fun onResume() {
        super.onResume()
        dialog?.takeIf { isLandscape }?.let {
            val sheet = it as BottomSheetDialog
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    private fun sendResultAndDismiss(result: MessageSheetResult) {
        findNavController()
            .getBackStackEntry(R.id.mainFragment)
            .savedStateHandle[MainFragment.MESSAGE_SHEET_RESULT_KEY] = result
        dialog?.dismiss()
    }

    private fun showTimeoutDialog() {
        var currentItem = 0
        val choices = resources.getStringArray(R.array.timeout_entries)
        val dialogContent = TimeoutDialogBinding.inflate(LayoutInflater.from(requireContext()), null, false).apply {
            timeoutSlider.setLabelFormatter { choices[it.toInt()] }
            timeoutSlider.addOnChangeListener { _, value, _ ->
                currentItem = value.toInt()
                timeoutValue.text = choices[value.toInt()]
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_user_timeout_title)
            .setView(dialogContent.root)
            .setPositiveButton(R.string.confirm_user_timeout_positive_button) { _, _ ->
                lifecycleScope.launch {
                    viewModel.timeoutUser(currentItem)
                    dialog?.dismiss()
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showBanDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_user_ban_title)
            .setMessage(R.string.confirm_user_ban_message)
            .setPositiveButton(R.string.confirm_user_ban_positive_button) { _, _ ->
                lifecycleScope.launch {
                    viewModel.banUser()
                    dialog?.dismiss()
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private fun showDeleteDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_user_delete_title)
            .setMessage(R.string.confirm_user_delete_message)
            .setPositiveButton(R.string.confirm_user_delete_positive_button) { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteMessage()
                    dialog?.dismiss()
                }
            }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
            .show()
    }
}
