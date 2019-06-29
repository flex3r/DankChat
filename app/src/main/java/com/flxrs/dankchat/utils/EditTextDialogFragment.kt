package com.flxrs.dankchat.utils

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

class EditTextDialogFragment(
		@StringRes private val title: Int,
		@StringRes private val negativeButtonText: Int,
		@StringRes private val positiveButtonText: Int,
		@StringRes private val message: Int = -1,
		private val textHint: CharSequence,
		private val dialogCallback: (String) -> Unit
) : DialogFragment() {

	private lateinit var binding: EdittextDialogBinding

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		binding = DataBindingUtil.inflate(LayoutInflater.from(requireContext()), R.layout.edittext_dialog, null, false)
		binding.dialogEdit.hint = textHint
		val builder = MaterialAlertDialogBuilder(requireContext())
				.setTitle(title)
				.setView(binding.root)
				.setNegativeButton(negativeButtonText) { _, _ -> dismiss() }
				.setPositiveButton(positiveButtonText) { _, _ -> getInputAndDismiss() }
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
		if (input.isNotBlank()) dialogCallback(input)
		dismiss()
		return true
	}
}