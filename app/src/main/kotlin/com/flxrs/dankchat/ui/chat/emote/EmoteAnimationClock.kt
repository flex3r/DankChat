package com.flxrs.dankchat.ui.chat.emote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.flxrs.dankchat.utils.extensions.forEachLayer
import com.flxrs.dankchat.utils.extensions.setRunning
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Frames of all animated emotes due within one tick are shown together. Faster ticks than the frame rates of
// emotes keep their speed, a frame that is drawn more than one frame late slows its animation down.
private const val TICK_RATE_HZ = 60
private const val TICK_PERIOD_MILLIS = 1000.0 / TICK_RATE_HZ

/**
 * Advances animated emotes on shared ticks. Their frames are rendered off the main thread and shown together,
 * so the screen redraws once per tick instead of whenever a single emote reaches its next frame.
 */
internal class EmoteAnimationClock(
    private val scope: CoroutineScope,
    private val renderDispatcher: CoroutineDispatcher,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = LinkedHashSet<ClockedAnimationDrawable>()
    private var isTickScheduled = false
    private var isRendering = false
    private var isPaused = false
    private val tick = Runnable { onTick() }

    fun add(drawable: ClockedAnimationDrawable) {
        running += drawable
        scheduleTick()
    }

    fun remove(drawable: ClockedAnimationDrawable) {
        running -= drawable
    }

    // Nothing is drawn while the app is in the background, a paused clock renders no frames
    fun setPaused(paused: Boolean) {
        isPaused = paused
        when {
            paused -> {
                mainHandler.removeCallbacks(tick)
                isTickScheduled = false
            }

            else -> {
                scheduleTick()
            }
        }
    }

    private fun scheduleTick() {
        if (isTickScheduled || isRendering || isPaused) {
            return
        }
        val nextFrame = running.minOfOrNull { it.nextFrameUptimeMillis } ?: return
        if (nextFrame == Long.MAX_VALUE) {
            return
        }
        // Rounded up to the tick grid, so frames due close together render in the same tick
        val due = max(nextFrame, SystemClock.uptimeMillis())
        val tickTime = (ceil(due / TICK_PERIOD_MILLIS) * TICK_PERIOD_MILLIS).toLong()
        isTickScheduled = true
        mainHandler.postAtTime(tick, tickTime)
    }

    private fun onTick() {
        isTickScheduled = false
        val now = SystemClock.uptimeMillis()
        val due = running.filter { it.nextFrameUptimeMillis <= now }
        if (due.isEmpty()) {
            scheduleTick()
            return
        }
        isRendering = true
        scope.launch {
            try {
                withContext(renderDispatcher) {
                    due.forEach { it.renderNextFrame() }
                }
                due.forEach { it.showRenderedFrame() }
            } finally {
                isRendering = false
                scheduleTick()
            }
        }
    }
}

/** Shows an animated drawable through frames rendered by [EmoteAnimationClock], instead of animating it on the RenderThread. */
internal class ClockedAnimationDrawable(
    val source: Drawable,
    private val clock: EmoteAnimationClock,
) : Drawable(),
    Animatable {
    private val width = source.bounds.width().coerceAtLeast(1)
    private val height = source.bounds.height().coerceAtLeast(1)
    private var shownFrame = FrameBuffer(width, height)
    private var renderedFrame = FrameBuffer(width, height)
    val frameBufferBytes = shownFrame.bitmap.allocationByteCount.toLong() + renderedFrame.bitmap.allocationByteCount
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var isAnimating = false

    // Software drawing makes the animated layers schedule their next frame instead of animating on the RenderThread
    private var scheduledFrameUptimeMillis = Long.MAX_VALUE
    private val sourceCallback =
        object : Callback {
            override fun invalidateDrawable(who: Drawable) = Unit

            override fun scheduleDrawable(
                who: Drawable,
                what: Runnable,
                `when`: Long,
            ) {
                scheduledFrameUptimeMillis = min(scheduledFrameUptimeMillis, `when`)
            }

            override fun unscheduleDrawable(
                who: Drawable,
                what: Runnable,
            ) = Unit
        }

    // Written on the render thread, applied on the main thread together with the rendered frame
    private var renderedNextFrameUptimeMillis = Long.MAX_VALUE

    var nextFrameUptimeMillis = Long.MAX_VALUE
        private set

    init {
        bounds = source.bounds
        source.callback = sourceCallback
        render(shownFrame)
    }

    // Called on the render thread, the shown frame stays untouched until showRenderedFrame swaps them
    fun renderNextFrame() {
        renderedNextFrameUptimeMillis = render(renderedFrame)
    }

    fun showRenderedFrame() {
        renderedFrame = shownFrame.also { shownFrame = renderedFrame }
        nextFrameUptimeMillis =
            when {
                renderedNextFrameUptimeMillis != Long.MAX_VALUE -> renderedNextFrameUptimeMillis

                // A frame due in less than a millisecond schedules nothing, a running animation is drawn again on the next tick
                isAnimating && source.hasRunningAnimation -> SystemClock.uptimeMillis() + 1

                else -> Long.MAX_VALUE
            }
        invalidateSelf()
    }

    private fun render(frame: FrameBuffer): Long {
        scheduledFrameUptimeMillis = Long.MAX_VALUE
        frame.bitmap.eraseColor(Color.TRANSPARENT)
        frame.canvas.withTranslation(-source.bounds.left.toFloat(), -source.bounds.top.toFloat()) { source.draw(this) }
        return scheduledFrameUptimeMillis
    }

    override fun start() {
        if (isAnimating) {
            return
        }
        isAnimating = true
        source.setAnimationsRunning(true)
        // The first frame after starting is rendered on the next tick
        nextFrameUptimeMillis = SystemClock.uptimeMillis()
        clock.add(this)
    }

    override fun stop() {
        if (!isAnimating) {
            return
        }
        isAnimating = false
        clock.remove(this)
        source.setAnimationsRunning(false)
    }

    override fun isRunning(): Boolean = isAnimating

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(shownFrame.bitmap, null, bounds, paint)
    }

    override fun getIntrinsicWidth(): Int = width

    override fun getIntrinsicHeight(): Int = height

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private class FrameBuffer(
    width: Int,
    height: Int,
) {
    val bitmap: Bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
}

internal val Drawable.isAnimated: Boolean
    get() = when (this) {
        is LayerDrawable -> (0 until numberOfLayers).any { getDrawable(it) is Animatable }
        else -> this is Animatable
    }

private val Drawable.hasRunningAnimation: Boolean
    get() = when (this) {
        is LayerDrawable -> (0 until numberOfLayers).any { (getDrawable(it) as? Animatable)?.isRunning == true }
        else -> this is Animatable && isRunning
    }

internal fun Drawable.setAnimationsRunning(running: Boolean) {
    when (this) {
        is LayerDrawable -> forEachLayer<Animatable> { it.setRunning(running) }
        is Animatable -> setRunning(running)
    }
}
