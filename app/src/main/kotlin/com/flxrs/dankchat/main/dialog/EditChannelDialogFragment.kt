package com.flxrs.dankchat.main.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.navArgs
import com.flxrs.dankchat.R
import com.flxrs.dankchat.data.toUserName
import com.flxrs.dankchat.databinding.EditDialogBinding
import com.flxrs.dankchat.preferences.DankChatPreferenceStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EditChannelDialogFragment : DialogFragment() {

    private val args: EditChannelDialogFragmentArgs by navArgs()

    @Inject
    lateinit var dankChatPreferences: DankChatPreferenceStore

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = EditDialogBinding.inflate(layoutInflater, null, false).apply {
            dialogEdit.hint = args.channelWithRename.rename?.value ?: args.channelWithRename.channel.value
            dialogEdit.setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE -> getInputAndDismiss(dialogEdit.text)
                    else                       -> false
                }
            }

            dialogEditLayout.isEndIconVisible = args.channelWithRename.rename != null
            dialogEditLayout.setEndIconOnClickListener {
                val rename = args.channelWithRename.copy(rename = null)
                dankChatPreferences.setRenamedChannel(rename)
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
        val trimmedInput = input?.toString()?.trim()?.ifBlank { null }
        val rename = args.channelWithRename.copy(rename = trimmedInput?.toUserName())
        dankChatPreferences.setRenamedChannel(rename)
        dismiss()
        return true
    }
}
