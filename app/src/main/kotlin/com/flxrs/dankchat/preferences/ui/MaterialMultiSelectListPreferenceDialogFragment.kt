package com.flxrs.dankchat.preferences.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.preference.MultiSelectListPreference
import androidx.preference.MultiSelectListPreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MaterialMultiSelectListPreferenceDialogFragment : MultiSelectListPreferenceDialogFragmentCompat() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.dialogTitle)
            .setIcon(preference.dialogIcon)
            .setPositiveButton(preference.positiveButtonText, this)
            .setNegativeButton(preference.negativeButtonText, this)
        val contentView = onCreateDialogView(requireContext())
        if (contentView != null) {
            onBindDialogView(contentView)
            builder.setView(contentView)
        } else {
            builder.setMessage(preference.dialogMessage)
        }

        onPrepareDialogBuilder(builder)
        return builder.create()
    }

    /* Override the methods that access mWhichButtonClicked (because we cannot set it properly here) */

    /** Which button was clicked.  */
    private var mWhichButtonClicked = 0

    override fun onClick(dialog: DialogInterface, which: Int) {
        mWhichButtonClicked = which
    }

    override fun onDismiss(dialog: DialogInterface) {
        onDialogClosedWasCalledFromOnDismiss = true
        super.onDismiss(dialog)
    }

    private var onDialogClosedWasCalledFromOnDismiss = false

    override fun onDialogClosed(positiveResult: Boolean) {
        if (onDialogClosedWasCalledFromOnDismiss) {
            onDialogClosedWasCalledFromOnDismiss = false
            // this means the positiveResult needs to be calculated from our mWhichButtonClicked
            super.onDialogClosed(mWhichButtonClicked == DialogInterface.BUTTON_POSITIVE)
        } else {
            super.onDialogClosed(positiveResult)
        }
    }
}

@Suppress("DEPRECATION")
fun PreferenceFragmentCompat.showMultiSelectListPreferenceDialog(preference: MultiSelectListPreference) {
    val dialogFragment = MaterialMultiSelectListPreferenceDialogFragment().apply {
        arguments = Bundle(1).apply {
            putString("key", preference.key)
        }
    }
    dialogFragment.setTargetFragment(this, 0)
    dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
}