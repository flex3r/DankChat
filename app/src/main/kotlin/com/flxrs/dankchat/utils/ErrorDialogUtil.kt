package com.flxrs.dankchat.utils

import android.content.ClipboardManager
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.flxrs.dankchat.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

fun View.showErrorDialog(throwable: Throwable, stackTraceString: String = Log.getStackTraceString(throwable)) {
    val title = context.getString(R.string.error_dialog_title, throwable.javaClass.name)

    MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(stackTraceString)
        .setPositiveButton(R.string.error_dialog_copy) { d, _ ->
            ContextCompat.getSystemService(context, ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("error stacktrace", stackTraceString))
            Snackbar.make(rootView.findViewById(android.R.id.content), R.string.snackbar_error_copied, Snackbar.LENGTH_SHORT).show()
            d.dismiss()
        }
        .setNegativeButton(R.string.dialog_dismiss) { d, _ -> d.dismiss() }
        .show()
}