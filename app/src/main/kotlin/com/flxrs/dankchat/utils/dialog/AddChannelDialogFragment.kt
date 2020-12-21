package com.flxrs.dankchat.utils.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.EdittextDialogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddChannelDialogFragment : DialogFragment() {

    private var bindingRef: EdittextDialogBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val message = args.getInt(MESSAGE_ARG)
        bindingRef = DataBindingUtil.inflate(LayoutInflater.from(requireContext()), R.layout.edittext_dialog, null, false)
        binding.dialogEdit.hint = args.getString(HINT_ARG)
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(args.getInt(TITLE_ARG))
            .setView(binding.root)
            .setNegativeButton(args.getInt(NEGATIVE_BUTTON_ARG)) { _, _ -> dismiss() }
            .setPositiveButton(args.getInt(POSITIVE_BUTTON_ARG)) { _, _ -> getInputAndDismiss() }
            .apply { if (message != -1) setMessage(message) }

        binding.dialogEdit.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> getInputAndDismiss()
                else -> false
            }
        }
        return builder.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }

    private fun getInputAndDismiss(): Boolean {
        val input = binding.dialogEdit.text.toString().trim()
        if (input.isNotBlank() && input.length > 2) {
            val activity = this.activity
            if (activity is AddChannelDialogResultHandler)
                activity.onAddChannelDialogResult(input)
        }
        dismiss()
        return true
    }

    companion object {
        fun create(
            @StringRes title: Int,
            @StringRes negativeButtonText: Int,
            @StringRes positiveButtonText: Int,
            @StringRes message: Int = -1,
            textHint: String
        ): AddChannelDialogFragment {
            val args = bundleOf(
                TITLE_ARG to title,
                NEGATIVE_BUTTON_ARG to negativeButtonText,
                POSITIVE_BUTTON_ARG to positiveButtonText,
                MESSAGE_ARG to message,
                HINT_ARG to textHint
            )

            return AddChannelDialogFragment().apply { arguments = args }
        }

        private const val TITLE_ARG = "title"
        private const val NEGATIVE_BUTTON_ARG = "negativeButton"
        private const val POSITIVE_BUTTON_ARG = "positiveButton"
        private const val MESSAGE_ARG = "message"
        private const val HINT_ARG = "hint"
    }
}