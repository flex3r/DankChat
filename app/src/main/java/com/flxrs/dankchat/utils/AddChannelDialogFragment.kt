package com.flxrs.dankchat.utils

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.DialogAddChannelBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddChannelDialogFragment(private val dialogCallback: (String) -> Unit) : DialogFragment() {

	private lateinit var binding: DialogAddChannelBinding

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		binding = DataBindingUtil.inflate(LayoutInflater.from(requireContext()), R.layout.dialog_add_channel, null, false)
		val builder = MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.dialog_title)
				.setView(binding.root)
				.setNegativeButton(R.string.dialog_negative_button) { _, _ -> dismiss() }
				.setPositiveButton(R.string.dialog_positive_button) { _, _ -> addChannelAndDismiss() }

		binding.editAddChannel.setOnEditorActionListener { _, actionId, _ ->
			return@setOnEditorActionListener when (actionId) {
				EditorInfo.IME_ACTION_DONE -> addChannelAndDismiss()
				else                       -> false
			}
		}
		return builder.create()
	}

	private fun addChannelAndDismiss(): Boolean {
		val input = binding.editAddChannel.text.toString().trim().toLowerCase()
		if (input.isNotBlank()) dialogCallback(input)
		dismiss()
		return true
	}
}