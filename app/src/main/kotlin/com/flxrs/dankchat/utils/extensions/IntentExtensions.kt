package com.flxrs.dankchat.utils.extensions

import android.content.Intent
import android.os.Build
import android.os.Parcelable

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(key, T::class.java)
    else                                                  -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}
