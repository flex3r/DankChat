package com.flxrs.dankchat.main

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.R
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DankChatInputLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.textInputStyle,
) : TextInputLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val INVALID_FINGER_INDEX = -1
        private val TAG = DankChatInputLayout::class.java.simpleName
    }

    private var endIconTouchListener: OnTouchListener? = null

    @SuppressLint("ClickableViewAccessibility")
    fun setEndIconTouchListener(touchListener: OnTouchListener?): Boolean {
        val imageButton = runCatching {
            val endLayout = javaClass.superclass.getDeclaredField("endLayout").let {
                it.isAccessible = true
                it.get(this)
            }
            endLayout.javaClass.getDeclaredField("endIconView").let {
                it.isAccessible = true
                it.get(endLayout) as View
            }
        }.getOrElse {
            Log.e(TAG, "Failed to access EndIcon ImageButton", it)
            return false
        }

        endIconTouchListener = touchListener

        if (touchListener == null) {
            imageButton.setOnTouchListener(null)
            return true
        }

        imageButton.isFocusable = true
        imageButton.isClickable = true

        var firstFingerIndex = INVALID_FINGER_INDEX
        var isHolding = false
        val callbackJob = Job()
        val cancelActionJob = {
            isHolding = false
            firstFingerIndex = INVALID_FINGER_INDEX
            callbackJob.cancelChildren()
        }

        val viewTouchListener = OnTouchListener { view: View, event: MotionEvent ->
            if (firstFingerIndex != INVALID_FINGER_INDEX && firstFingerIndex != event.getPointerId(event.actionIndex)) {
                return@OnTouchListener view.onTouchEvent(event)
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    firstFingerIndex = event.getPointerId(event.actionIndex)
                    view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(callbackJob) {
                        delay(500L)
                        isHolding = true
                        touchListener.onTouch(TouchEvent.LONG_CLICK)

                        delay(1_000L)
                        touchListener.onTouch(TouchEvent.HOLD_START)
                    } ?: return@OnTouchListener view.onTouchEvent(event)
                }

                MotionEvent.ACTION_MOVE -> Unit
                MotionEvent.ACTION_UP   -> {
                    when {
                        isHolding -> touchListener.onTouch(TouchEvent.HOLD_STOP)
                        else      -> touchListener.onTouch(TouchEvent.CLICK)
                    }
                    cancelActionJob()
                }

                else                    -> cancelActionJob()
            }

            false
        }

        imageButton.setOnTouchListener(viewTouchListener)
        return true
    }

    fun interface OnTouchListener {
        fun onTouch(touchEvent: TouchEvent)
    }

    enum class TouchEvent {
        CLICK,
        LONG_CLICK,
        HOLD_START,
        HOLD_STOP
    }
}
