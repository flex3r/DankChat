package com.flxrs.dankchat.main.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.channels.ChannelsDialogFragment
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.databinding.EditDialogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EditChannelDialogFragment : DialogFragment() {

    private val args: EditChannelDialogFragmentArgs by navArgs()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = EditDialogBinding.inflate(layoutInflater, null, false).apply {
            dialogEdit.hint = args.renaming?.value ?: args.channel.value
            dialogEdit.setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE -> getInputAndDismiss(dialogEdit.text)
                    else                       -> false
                }
            }

            dialogEditLayout.isEndIconVisible = args.renaming != null
            dialogEditLayout.setEndIconOnClickListener {
                findNavController()
                    .getBackStackEntry(R.id.channelsDialogFragment)
                    .savedStateHandle[ChannelsDialogFragment.RENAME_TAB_REQUEST_KEY] = args.channel to args.channel
                dismiss()
            }
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_dialog_title)
            .setView(binding.root)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> dismiss() }
            .setPositiveButton(R.string.dialog_ok) { _, _ -> getInputAndDismiss(binding.dialogEdit.text) }

        return builder.create()
    }

    private fun getInputAndDismiss(input: Editable?): Boolean {
        val trimmedInput = input?.toString()?.trim().orEmpty()
        if (trimmedInput.isNotBlank()) {
            findNavController()
                .getBackStackEntry(R.id.channelsDialogFragment)
                .savedStateHandle[ChannelsDialogFragment.RENAME_TAB_REQUEST_KEY] = args.channel to trimmedInput.toUserName()
        }
        dismiss()
        return true
    }
}
