package com.flxrs.dankchat.utils

import android.content.Context
import android.graphics.Insets
import android.os.Build
import android.util.AttributeSet
import android.view.WindowInsets
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowInsetsCompat

class FitsSystemWindowsExceptTopConstraintLayout : ConstraintLayout {

    constructor(context: Context) : super(context)

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    constructor(context: Context, attributeSet: AttributeSet, defStyleAttr: Int) : super(
        context,
        attributeSet,
        defStyleAttr
    )

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets? {
        setPadding(
            insets.systemWindowInsetLeft,
            0,
            insets.systemWindowInsetRight,
            insets.systemWindowInsetBottom
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowInsets.Builder(insets)
                .setSystemWindowInsets(Insets.of(0, insets.systemWindowInsetTop, 0, 0))
                .build()
        } else {
            WindowInsetsCompat.toWindowInsetsCompat(insets)
                .replaceSystemWindowInsets(0, insets.systemWindowInsetTop, 0, 0).toWindowInsets()
        }
    }
}