package com.flxrs.dankchat.utils

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.DialogAddChannelBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddChannelDialogFragment(private val dialogCallback: (String) -> Unit) : DialogFragment(), TextView.OnEditorActionListener {

	private lateinit var binding: DialogAddChannelBinding

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		binding = DataBindingUtil.inflate(LayoutInflater.from(requireContext()), R.layout.dialog_add_channel, null, false)
		val builder = MaterialAlertDialogBuilder(requireContext())
				.setTitle(R.string.dialog_title)
				.setView(binding.root)
				.setNegativeButton(R.string.dialog_negative_button) { _, _ -> dismiss() }
				.setPositiveButton(R.string.dialog_positive_button) { _, _ ->
					val input = binding.editAddChannel.text.toString().trim()
					if (input.isNotBlank()) {
						dialogCallback(input)
					}

					dismiss()
				}
		return builder.create()
	}

	override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
		if (EditorInfo.IME_ACTION_DONE == actionId) {
			val input = binding.editAddChannel.text.toString().trim()
			if (input.isNotBlank()) {
				dialogCallback(input)
			}

			dismiss()
			return true
		}
		return false
	}
}