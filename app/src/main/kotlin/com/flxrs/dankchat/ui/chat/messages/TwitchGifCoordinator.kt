package com.flxrs.dankchat.ui.chat.messages

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.flxrs.dankchat.utils.extensions.setRunning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.koin.core.annotation.Single

private const val TWITCH_GIF_CACHE_SIZE_KIB = 24 * 1024
private const val TWITCH_GIF_CACHE_MAX_ENTRIES = 24
private const val ANIMATED_DRAWABLE_BUFFER_COUNT = 4L
private const val BYTES_PER_PIXEL = 4L
private const val BYTES_PER_KIB = 1024L

@Single
internal class TwitchGifCoordinator {
    private val listeners = HashMap<Drawable, MutableMap<() -> Unit, Boolean>>()
    private val cache =
        object : LruCache<String, Drawable>(TWITCH_GIF_CACHE_SIZE_KIB) {
            override fun sizeOf(
                key: String,
                value: Drawable,
            ): Int {
                val width = value.intrinsicWidth.coerceAtLeast(1).toLong()
                val height = value.intrinsicHeight.coerceAtLeast(1).toLong()
                val estimatedSize = width * height * BYTES_PER_PIXEL * ANIMATED_DRAWABLE_BUFFER_COUNT / BYTES_PER_KIB
                val minimumEntryCharge = TWITCH_GIF_CACHE_SIZE_KIB / TWITCH_GIF_CACHE_MAX_ENTRIES
                return estimatedSize.coerceIn(minimumEntryCharge.toLong(), Int.MAX_VALUE.toLong()).toInt()
            }

            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: Drawable,
                newValue: Drawable?,
            ) {
                if (oldValue !== newValue && oldValue !in listeners) {
                    (oldValue as? Animatable)?.stop()
                    oldValue.callback = null
                }
            }
        }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlightLoads = mutableMapOf<String, CompletableDeferred<LoadResult>>()
    private val callback =
        object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                listeners[who]?.forEach { (listener, animate) ->
                    if (animate) listener()
                }
            }

            override fun scheduleDrawable(
                who: Drawable,
                what: Runnable,
                `when`: Long,
            ) {
                mainHandler.postAtTime(what, who, `when`)
            }

            override fun unscheduleDrawable(
                who: Drawable,
                what: Runnable,
            ) {
                mainHandler.removeCallbacks(what, who)
            }
        }

    fun get(key: String): Drawable? = cache.get(key)

    fun put(
        key: String,
        drawable: Drawable,
    ) {
        cache.put(key, drawable)
    }

    suspend fun getOrLoad(
        key: String,
        loader: suspend () -> Drawable?,
    ): Drawable? {
        get(key)?.let { return it }

        while (true) {
            val candidate = CompletableDeferred<LoadResult>()
            val (load, isOwner) =
                synchronized(inFlightLoads) {
                    inFlightLoads[key]?.let { it to false }
                        ?: candidate.also { inFlightLoads[key] = it }.let { it to true }
                }

            if (!isOwner) {
                when (val result = load.await()) {
                    LoadResult.Cancelled -> continue
                    is LoadResult.Completed -> return result.drawable
                }
            }

            try {
                val drawable = loader()
                drawable?.let { put(key, it) }
                load.complete(LoadResult.Completed(drawable))
                return drawable
            } catch (e: CancellationException) {
                load.complete(LoadResult.Cancelled)
                throw e
            } catch (t: Throwable) {
                load.completeExceptionally(t)
                throw t
            } finally {
                synchronized(inFlightLoads) {
                    inFlightLoads.remove(key, load)
                }
            }
        }
    }

    fun register(
        drawable: Drawable,
        listener: () -> Unit,
        animate: Boolean,
    ) {
        listeners.getOrPut(drawable) { mutableMapOf() }[listener] = animate
        drawable.callback = callback
        updateAnimation(drawable)
    }

    fun unregister(
        drawable: Drawable,
        listener: () -> Unit,
    ) {
        val drawableListeners = listeners[drawable] ?: return
        drawableListeners -= listener
        if (drawableListeners.isEmpty()) {
            listeners -= drawable
            (drawable as? Animatable)?.stop()
            drawable.callback = null
        } else {
            updateAnimation(drawable)
        }
    }

    private fun updateAnimation(drawable: Drawable) {
        (drawable as? Animatable)?.setRunning(listeners[drawable]?.values?.any { it } == true)
    }

    private sealed interface LoadResult {
        data object Cancelled : LoadResult

        data class Completed(
            val drawable: Drawable?,
        ) : LoadResult
    }
}
