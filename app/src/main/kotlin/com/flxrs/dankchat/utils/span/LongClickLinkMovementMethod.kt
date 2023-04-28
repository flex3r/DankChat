package com.flxrs.dankchat.utils.span

import android.os.Handler
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.widget.TextView
import androidx.core.os.postDelayed

object LongClickLinkMovementMethod : LinkMovementMethod() {
    private const val LONG_CLICK_TIME = 500L
    private const val CLICKABLE_OFFSET = 10
    private var isLongPressed = false
    private val longClickHandler = Handler(Looper.getMainLooper())

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (val action = event.action) {
            MotionEvent.ACTION_CANCEL                      -> longClickHandler.removeCallbacksAndMessages(null)
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
                if (linkSpans.isEmpty()) {
                    return super.onTouchEvent(widget, buffer, event)
                }

                val span = linkSpans.find {
                    if (!it.checkBounds) {
                        return@find true
                    }

                    val start = buffer.getSpanStart(it)
                    val end = buffer.getSpanEnd(it)
                    val startPos = layout.getPrimaryHorizontal(start).toInt()
                    val endPos = layout.getPrimaryHorizontal(end).toInt()
                    val range = when {
                        startPos <= endPos -> startPos - CLICKABLE_OFFSET..endPos + CLICKABLE_OFFSET
                        else               -> endPos - CLICKABLE_OFFSET..startPos + CLICKABLE_OFFSET
                    }
                    x in range
                } ?: return true

                if (action == MotionEvent.ACTION_UP) {
                    longClickHandler.removeCallbacksAndMessages(null)
                    if (!isLongPressed) {
                        span.onClick(widget)
                    }
                    isLongPressed = false
                } else {
                    Selection.setSelection(buffer, buffer.getSpanStart(span), buffer.getSpanEnd(span))
                    longClickHandler.postDelayed(LONG_CLICK_TIME) {
                        span.onLongClick(widget)
                        isLongPressed = true
                    }
                }

                return true
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }
}
