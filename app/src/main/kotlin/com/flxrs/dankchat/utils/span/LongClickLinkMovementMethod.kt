package com.flxrs.dankchat.utils.span

import android.os.Handler
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.view.MotionEvent
import android.widget.TextView
import androidx.core.os.postDelayed

class LongClickLinkMovementMethod : LinkMovementMethod() {
    private var longClickHandler: Handler? = null
    private var isLongPressed = false

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (val action = event.action) {
            MotionEvent.ACTION_CANCEL -> longClickHandler?.removeCallbacksAndMessages(null)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_DOWN -> {
                var x = event.x.toInt()
                var y = event.y.toInt()
                x -= widget.totalPaddingLeft
                y -= widget.totalPaddingTop
                x += widget.scrollX
                y += widget.scrollY

                val layout = widget.layout
                val line = layout.getLineForVertical(y)
                val offset = layout.getOffsetForHorizontal(line, x.toFloat())

                val linkSpans = buffer.getSpans(offset, offset, LongClickableSpan::class.java)
                if (linkSpans.isEmpty()) return super.onTouchEvent(widget, buffer, event)

                if (action == MotionEvent.ACTION_UP) {
                    longClickHandler?.removeCallbacksAndMessages(null)
                    if (!isLongPressed) {
                        linkSpans[0].onClick(widget)
                    }
                    isLongPressed = false
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(linkSpans[0]), buffer.getSpanEnd(linkSpans[0]))
                    longClickHandler?.postDelayed(LONG_CLICK_TIME) {
                        linkSpans[0].onLongClick(widget)
                        isLongPressed = true
                    }
                }

                return true
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    companion object {
        private const val LONG_CLICK_TIME = 500L
        val instance: MovementMethod?
            get() {
                if (sInstance == null) {
                    sInstance = LongClickLinkMovementMethod().apply {
                        longClickHandler = Handler(Looper.getMainLooper())
                    }
                }
                return sInstance
            }

        private var sInstance: LongClickLinkMovementMethod? = null
    }
}