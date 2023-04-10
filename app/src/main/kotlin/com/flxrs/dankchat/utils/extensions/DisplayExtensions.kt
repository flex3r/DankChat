package com.flxrs.dankchat.utils.extensions

import android.content.res.Resources
import androidx.annotation.Px

val Int.dp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()

@get:Px
val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()
