package com.flxrs.dankchat.utils.extensions

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

fun <V : View> BottomSheetBehavior<V>.expand() {
    this.state = BottomSheetBehavior.STATE_EXPANDED
}

fun <V : View> BottomSheetBehavior<V>.hide() {
    this.state = BottomSheetBehavior.STATE_HIDDEN
}

inline val <V : View> BottomSheetBehavior<V>.isVisible: Boolean
    get() = this.state == BottomSheetBehavior.STATE_EXPANDED || this.state == BottomSheetBehavior.STATE_COLLAPSED

inline val <V : View> BottomSheetBehavior<V>.isCollapsed: Boolean
    get() = this.state == BottomSheetBehavior.STATE_COLLAPSED

inline val <V : View> BottomSheetBehavior<V>.isHidden: Boolean
    get() = this.state == BottomSheetBehavior.STATE_HIDDEN

inline val <V : View> BottomSheetBehavior<V>.isMoving: Boolean
    get() = this.state == BottomSheetBehavior.STATE_DRAGGING || this.state == BottomSheetBehavior.STATE_SETTLING

suspend fun <T : View> BottomSheetBehavior<T>.awaitState(targetState: Int) {
    if (state == targetState) {
        return
    }

    return suspendCancellableCoroutine {
        val callback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == targetState) {
                    removeBottomSheetCallback(this)
                    it.resume(Unit)
                }
            }
        }
        addBottomSheetCallback(callback)
        it.invokeOnCancellation { removeBottomSheetCallback(callback) }
        state = targetState
    }
}

