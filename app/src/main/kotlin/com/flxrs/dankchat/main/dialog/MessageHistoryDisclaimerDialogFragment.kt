package com.flxrs.dankchat.main.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.flxrs.dankchat.R
import com.flxrs.dankchat.main.MainFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MessageHistoryDisclaimerDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val spannable = SpannableStringBuilder(getString(R.string.message_history_disclaimer_message))
        Linkify.addLinks(spannable, Linkify.WEB_URLS)

        return MaterialAlertDialogBuilder(requireContext())
            .setCancelable(false)
            .setTitle(R.string.message_history_disclaimer_title)
            .setMessage(spannable)
            .setPositiveButton(R.string.dialog_optin) { _, _ -> dismissAndHandleResult(true) }
            .setNegativeButton(R.string.dialog_optout) { _, _ -> dismissAndHandleResult(false) }
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.apply {
            setCanceledOnTouchOutside(false)
            findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun dismissAndHandleResult(result: Boolean): Boolean {
        findNavController()
            .getBackStackEntry(R.id.mainFragment)
            .savedStateHandle
            .set(MainFragment.HISTORY_DISCLAIMER_KEY, result)
        dismiss()
        return true
    }
}