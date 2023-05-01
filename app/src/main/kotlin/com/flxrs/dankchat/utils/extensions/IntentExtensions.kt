package com.flxrs.dankchat.utils.extensions

import android.content.Intent
import android.os.Parcelable
import androidx.core.content.IntentCompat

// https://issuetracker.google.com/issues/240585930
inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = IntentCompat.getParcelableExtra(this, key, T::class.java)
