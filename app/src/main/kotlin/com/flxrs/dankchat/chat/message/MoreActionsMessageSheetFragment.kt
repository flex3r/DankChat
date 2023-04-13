package com.flxrs.dankchat.chat.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.MoreActionsMessageBottomsheetBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.isLandscape
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MoreActionsMessageSheetFragment : BottomSheetDialogFragment() {

    private val args: MoreActionsMessageSheetFragmentArgs by navArgs()
    private var bindingRef: MoreActionsMessageBottomsheetBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = MoreActionsMessageBottomsheetBinding.inflate(inflater, container, false).apply {
            messageCopyFull.setOnClickListener { sendResultAndDismiss(MoreActionsMessageSheetResult.Copy(args.fullMessage)) }
            messageCopyId.setOnClickListener { sendResultAndDismiss(MoreActionsMessageSheetResult.CopyId(args.messageId)) }
        }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        dialog?.takeIf { isLandscape }?.let {
            with(it as BottomSheetDialog) {
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }

    private fun sendResultAndDismiss(result: MoreActionsMessageSheetResult) {
        findNavController()
            .getBackStackEntry(R.id.mainFragment)
            .savedStateHandle[MainFragment.COPY_MESSAGE_SHEET_RESULT_KEY] = result
        dialog?.dismiss()
    }
}
