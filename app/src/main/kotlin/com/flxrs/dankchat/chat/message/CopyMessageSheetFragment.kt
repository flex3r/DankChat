package com.flxrs.dankchat.chat.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.CopyMessageBottomsheetBinding
import com.flxrs.dankchat.main.MainFragment
import com.flxrs.dankchat.utils.extensions.isLandscape
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CopyMessageSheetFragment : BottomSheetDialogFragment() {

    private val args: CopyMessageSheetFragmentArgs by navArgs()
    private var bindingRef: CopyMessageBottomsheetBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        bindingRef = CopyMessageBottomsheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.messageCopy.setOnClickListener { sendResultAndDismiss(CopyMessageSheetResult.Copy(args.message)) }
        binding.messageCopyFull.setOnClickListener { sendResultAndDismiss(CopyMessageSheetResult.Copy(args.fullMessage)) }
        binding.messageCopyId.setOnClickListener { sendResultAndDismiss(CopyMessageSheetResult.CopyId(args.messageId)) }

        dialog?.takeIf { isLandscape }?.let {
            val sheet = it as BottomSheetDialog
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }

    private fun sendResultAndDismiss(result: CopyMessageSheetResult) {
        findNavController()
            .getBackStackEntry(R.id.mainFragment)
            .savedStateHandle[MainFragment.COPY_MESSAGE_SHEET_RESULT_KEY] = result
        dialog?.dismiss()
    }
}
