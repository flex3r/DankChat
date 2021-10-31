package com.flxrs.dankchat.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class GetImageOrVideoContract : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = when {
        intent == null || resultCode != Activity.RESULT_OK -> null
        else                                               -> intent.data
    }
}