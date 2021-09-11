package com.flxrs.dankchat.main.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.EditChannelDialogBinding
import com.flxrs.dankchat.main.MainFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EditChannelDialogFragment : DialogFragment() {

    private val args: EditChannelDialogFragmentArgs by navArgs()
    private var bindingRef: EditChannelDialogBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        bindingRef = EditChannelDialogBinding.inflate(layoutInflater, null, false)
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("")
            .setView(binding.root)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> dismiss() }
            .setPositiveButton(R.string.dialog_ok) { _, _ -> getInputAndDismiss(binding.dialogEdit.text) }

        binding.dialogEdit.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> getInputAndDismiss(binding.dialogEdit.text)
                else                       -> false
            }
        }
        return builder.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bindingRef = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        findNavController().popBackStack(R.id.channelsDialogFragment, false)
    }

    private fun getInputAndDismiss(input: Editable?): Boolean {
        val trimmedInput = input?.toString()?.trim().orEmpty()
        if (trimmedInput.isNotBlank()) {
            with(findNavController()) {
                getBackStackEntry(R.id.mainFragment)
                    .savedStateHandle
                    .set(MainFragment.RENAME_TAB_REQUEST_KEY, "${args.channel}=$trimmedInput")
            }
        }
        dismiss()
        return true
    }
}