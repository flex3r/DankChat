package com.flxrs.dankchat.utils

import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.Callback
import android.view.View
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

class MultiCallback : Callback {

    private val callbacks = CopyOnWriteArrayList<CallbackReference>()

    override fun invalidateDrawable(who: Drawable) {
        callbacks.forEach { reference ->
            when (val callback = reference.get()) {
                null -> callbacks.remove(reference)
                else -> {
                    when (callback) {
                        is View -> callback.invalidate()
                        else    -> callback.invalidateDrawable(who)
                    }
                }
            }
        }
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        callbacks.forEach { reference ->
            when (val callback = reference.get()) {
                null -> callbacks.remove(reference)
                else -> callback.scheduleDrawable(who, what, `when`)
            }
        }
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        callbacks.forEach { reference ->
            when (val callback = reference.get()) {
                null -> callbacks.remove(reference)
                else -> callback.unscheduleDrawable(who, what)
            }
        }
    }

    fun addView(callback: Callback) {
        callbacks.forEach {
            val item = it.get()
            if (item == null) {
                callbacks.remove(it)
            }
        }
        callbacks.addIfAbsent(CallbackReference(callback))
    }

    fun removeView(callback: Callback) {
        callbacks.forEach {
            val item = it.get()
            if (item == null || item == callback) {
                callbacks.remove(it)
            }
        }
    }

    private data class CallbackReference(val callback: Callback?) : WeakReference<Callback>(callback)
}
