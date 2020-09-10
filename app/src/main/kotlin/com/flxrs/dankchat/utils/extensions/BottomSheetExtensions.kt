package com.flxrs.dankchat.utils.extensions

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior

fun <V : View> BottomSheetBehavior<V>.expand() {
    this.state = BottomSheetBehavior.STATE_EXPANDED
}

fun <V : View> BottomSheetBehavior<V>.hide() {
    this.state = BottomSheetBehavior.STATE_HIDDEN
}

val <V : View> BottomSheetBehavior<V>.isVisible: Boolean
    get() = this.state == BottomSheetBehavior.STATE_EXPANDED || this.state == BottomSheetBehavior.STATE_COLLAPSED

val <V : View> BottomSheetBehavior<V>.isMoving: Boolean
    get() = this.state == BottomSheetBehavior.STATE_DRAGGING || this.state == BottomSheetBehavior.STATE_SETTLING
