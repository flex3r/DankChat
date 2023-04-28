package com.flxrs.dankchat.utils.span

import android.text.style.ClickableSpan
import android.view.View

abstract class LongClickableSpan(val checkBounds: Boolean = true) : ClickableSpan() {
    abstract fun onLongClick(view: View)
}
