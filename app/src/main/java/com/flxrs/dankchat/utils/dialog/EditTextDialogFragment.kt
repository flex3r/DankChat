package com.flxrs.dankchat.utils.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.EdittextDialogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EditTextDialogFragment : DialogFragment() {

    private lateinit var binding: EdittextDialogBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val message = args.getInt(MESSAGE_ARG)
        binding = DataBindingUtil.inflate(
            LayoutInflater.from(requireContext()),
            R.layout.edittext_dialog,
            null,
            false
        )
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
                else                       -> false
            }
        }
        return builder.create()
    }

    private fun getInputAndDismiss(): Boolean {
        val input = binding.dialogEdit.text.toString().trim()
        if (input.isNotBlank()) {
            val activity = this.activity
            if (requireArguments().getBoolean(IS_ADD_CHANNEL) && activity is AddChannelDialogResultHandler) activity.onAddChannelDialogResult(
                input
            )
            else if (activity is AdvancedLoginDialogResultHandler) activity.onAdvancedLoginDialogResult(
                input
            )
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
            textHint: String,
            isAddChannel: Boolean = true
        ): EditTextDialogFragment {
            val args = Bundle().apply {
                putInt(TITLE_ARG, title)
                putInt(NEGATIVE_BUTTON_ARG, negativeButtonText)
                putInt(POSITIVE_BUTTON_ARG, positiveButtonText)
                putInt(MESSAGE_ARG, message)
                putString(HINT_ARG, textHint)
                putBoolean(IS_ADD_CHANNEL, isAddChannel)
            }

            return EditTextDialogFragment().apply { arguments = args }
        }

        private const val TITLE_ARG = "title"
        private const val NEGATIVE_BUTTON_ARG = "negativeButton"
        private const val POSITIVE_BUTTON_ARG = "positiveButton"
        private const val MESSAGE_ARG = "message"
        private const val HINT_ARG = "hint"
        private const val IS_ADD_CHANNEL = "isAddChannel"
    }
}