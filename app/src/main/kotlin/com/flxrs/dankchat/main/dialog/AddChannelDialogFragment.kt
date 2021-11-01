package com.flxrs.dankchat.main.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.AddChannelDialogBinding
import com.flxrs.dankchat.main.MainFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddChannelDialogFragment : DialogFragment() {

    private var bindingRef: AddChannelDialogBinding? = null
    private val binding get() = bindingRef!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        bindingRef = AddChannelDialogBinding.inflate(layoutInflater, null, false)
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_dialog_title)
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

    private fun getInputAndDismiss(input: Editable?): Boolean {
        val trimmedInput = input?.toString()?.trim().orEmpty()
        if (trimmedInput.isNotBlank()) {
            with(findNavController()) {
                getBackStackEntry(R.id.mainFragment)
                    .savedStateHandle
                    .set(MainFragment.ADD_CHANNEL_REQUEST_KEY, trimmedInput)
            }
        }
        dismiss()
        return true
    }
}