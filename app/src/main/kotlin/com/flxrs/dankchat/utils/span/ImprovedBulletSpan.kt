package com.flxrs.dankchat.utils.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Path.Direction
import android.text.Layout
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import androidx.annotation.Px

class ImprovedBulletSpan(
    @Px private val bulletRadius: Int = STANDARD_BULLET_RADIUS,
    @Px private val gapWidth: Int = STANDARD_GAP_WIDTH,
    private val color: Int = STANDARD_COLOR
) : LeadingMarginSpan {

    companion object {
        // Bullet is slightly bigger to avoid aliasing artifacts on mdpi devices.
        private const val STANDARD_BULLET_RADIUS = 4
        private const val STANDARD_GAP_WIDTH = 2
        private const val STANDARD_COLOR = 0
    }

    private var mBulletPath: Path? = null

    override fun getLeadingMargin(first: Boolean): Int {
        return 2 * bulletRadius + gapWidth
    }

    override fun drawLeadingMargin(canvas: Canvas, paint: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) {
        if ((text as Spanned).getSpanStart(this) == start) {
            val style = paint.style
            val oldColor = paint.color

            paint.style = Paint.Style.FILL
            if (color != STANDARD_COLOR) {
                paint.color = color
            }

            val yPosition = when {
                layout != null -> layout.getLineBaseline(layout.getLineForOffset(start)).toFloat() - bulletRadius * 2f
                else           -> (top + bottom) / 2f
            }

            val xPosition = (x + dir * bulletRadius).toFloat()

            if (canvas.isHardwareAccelerated) {
                if (mBulletPath == null) {
                    mBulletPath = Path()
                    mBulletPath!!.addCircle(0.0f, 0.0f, bulletRadius.toFloat(), Direction.CW)
                }

                with(canvas) {
                    save()
                    translate(xPosition, yPosition)
                    drawPath(mBulletPath!!, paint)
                    restore()
                }
            } else {
                canvas.drawCircle(xPosition, yPosition, bulletRadius.toFloat(), paint)
            }

            paint.style = style
            paint.color = oldColor
        }
    }
}
