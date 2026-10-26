package com.flxrs.dankchat.ui.chat.emote

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single

private const val EMOTE_CACHE_SIZE_KIB = 64 * 1024
private const val EMOTE_CACHE_MAX_ENTRIES = 512
private const val LAYER_CACHE_SIZE_KIB = 32 * 1024
private const val LAYER_CACHE_MAX_ENTRIES = 256
private const val ANIMATED_DRAWABLE_BUFFER_COUNT = 4L
private const val BYTES_PER_PIXEL = 4L
private const val BYTES_PER_KIB = 1024L

@Single
@Stable
class EmoteAnimationCoordinator(
    private val chatSettingsDataStore: ChatSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) {
    private val emoteCache = drawableCache<Drawable>(EMOTE_CACHE_SIZE_KIB, EMOTE_CACHE_MAX_ENTRIES)
    private val layerCache = drawableCache<Drawable>(LAYER_CACHE_SIZE_KIB, LAYER_CACHE_MAX_ENTRIES)
    private val dimensionCache = LruCache<String, Pair<Int, Int>>(1024)

    // Stacks with failed layers render only what loaded and retry on their next occurrence,
    // rows size them by these until the complete stack replaces them
    private val partialDimensionCache = LruCache<String, Pair<Int, Int>>(256)

    // Rows waiting for emote dimensions listen per key, so a put only wakes the rows showing that emote
    private val dimensionListeners = HashMap<String, MutableSet<() -> Unit>>()

    // Cache hits never publish dimensions again, so an entry evicted while its drawable is still
    // cached is restored from the drawable bounds instead of leaving rows on the estimated size
    fun getDimensions(key: String): Pair<Int, Int>? {
        dimensionCache.get(key)?.let { return it }
        val drawable = layerCache.get(key) ?: emoteCache.get(key) ?: return null
        val dimensions = drawable.bounds.width() to drawable.bounds.height()
        dimensionCache.put(key, dimensions)
        return dimensions
    }

    fun putDimensions(
        key: String,
        dimensions: Pair<Int, Int>,
    ) {
        partialDimensionCache.remove(key)
        val previous = dimensionCache.put(key, dimensions)
        if (previous != dimensions) {
            dimensionListeners[key]?.forEach { it() }
        }
    }

    fun getPartialDimensions(key: String): Pair<Int, Int>? = partialDimensionCache.get(key)

    fun putPartialDimensions(
        key: String,
        dimensions: Pair<Int, Int>,
    ) {
        val previous = partialDimensionCache.put(key, dimensions)
        if (previous != dimensions) {
            dimensionListeners[key]?.forEach { it() }
        }
    }

    // Emits once right away, dimensions put between composition and collection would otherwise be missed
    fun dimensionChanges(keys: Set<String>): Flow<Unit> = callbackFlow {
        val listener: () -> Unit = { trySend(Unit) }
        keys.forEach { dimensionListeners.getOrPut(it, ::HashSet).add(listener) }
        trySend(Unit)
        awaitClose {
            keys.forEach { key ->
                val listeners = dimensionListeners[key] ?: return@forEach
                listeners.remove(listener)
                if (listeners.isEmpty()) {
                    dimensionListeners.remove(key)
                }
            }
        }
    }.conflate()

    private val scope = CoroutineScope(SupervisorJob() + dispatchersProvider.immediate)
    private val animationClock = EmoteAnimationClock(scope, dispatchersProvider.default.limitedParallelism(1))
    private val pendingLoads = HashMap<String, Deferred<Drawable?>>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val invalidationListeners = HashMap<Drawable, MutableSet<() -> Unit>>()
    private val pendingStops = HashMap<Drawable, Runnable>()

    // Drawables are shared between all visible occurrences of an emote, but a Drawable only has
    // a single callback slot, so invalidations are fanned out to every registered listener
    private val fanOutCallback =
        object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                invalidationListeners[who]?.forEach { it() }
            }

            override fun scheduleDrawable(
                who: Drawable,
                what: Runnable,
                `when`: Long,
            ) {
                mainHandler.postAtTime(what, `when`)
            }

            override fun unscheduleDrawable(
                who: Drawable,
                what: Runnable,
            ) {
                mainHandler.removeCallbacks(what)
            }
        }

    // Only drawables with a visible occurrence run, so a settings change only touches the registered ones
    init {
        chatSettingsDataStore.currentSettings
            .map { it.animateGifs }
            .distinctUntilChanged()
            .onEach { animateGifs -> invalidationListeners.keys.forEach { it.setAnimationsRunning(animateGifs) } }
            .launchIn(scope)
    }

    // Occurrences composed together share one load per key. The load outlives the occurrence that
    // started it, so the remaining occurrences still get its result.
    suspend fun loadShared(
        key: String,
        load: suspend () -> Drawable?,
    ): Drawable? {
        val pending =
            pendingLoads[key] ?: scope
                .async(start = CoroutineStart.LAZY) {
                    try {
                        load()
                    } finally {
                        pendingLoads.remove(key)
                    }
                }.also {
                    // Registered before starting, a load that completes immediately must still remove itself
                    pendingLoads[key] = it
                    it.start()
                }
        return pending.await()
    }

    fun registerInvalidationListener(
        drawable: Drawable,
        listener: () -> Unit,
    ) {
        pendingStops.remove(drawable)?.let(mainHandler::removeCallbacks)
        val listeners = invalidationListeners.getOrPut(drawable) { mutableSetOf() }
        listeners += listener
        drawable.callback = fanOutCallback
        if (listeners.size == 1) {
            // Starting on the first visible occurrence phase-aligns emotes that enter the
            // viewport together, additional occurrences must not reset the running animation.
            // A drawable whose stop was just cancelled is still running, starting it again keeps its frame.
            drawable.setAnimationsRunning(chatSettingsDataStore.currentSettings.value.animateGifs)
        }
    }

    fun unregisterInvalidationListener(
        drawable: Drawable,
        listener: () -> Unit,
    ) {
        val listeners = invalidationListeners[drawable] ?: return
        listeners -= listener
        if (listeners.isEmpty()) {
            invalidationListeners.remove(drawable)
            // AnimatedImageDrawable animates on the RenderThread even when nothing draws it,
            // an explicit stop is the only thing that halts it while the drawable stays cached.
            // Deferred so an occurrence replaced within the same frame does not restart the animation from its first frame.
            val stop =
                Runnable {
                    pendingStops.remove(drawable)
                    drawable.setAnimationsRunning(false)
                    drawable.callback = null
                }
            pendingStops[drawable] = stop
            mainHandler.post(stop)
        }
    }

    fun setAnimationsPaused(paused: Boolean) {
        animationClock.setPaused(paused)
    }

    // Animated emotes advance on the shared clock, static ones are drawn as they are
    fun clockedIfAnimated(drawable: Drawable): Drawable = when {
        drawable.isAnimated -> ClockedAnimationDrawable(drawable, animationClock)
        else -> drawable
    }

    fun getCached(url: String): Drawable? = emoteCache.get(url)

    fun putInCache(
        url: String,
        drawable: Drawable,
    ) {
        emoteCache.put(url, drawable)
    }

    fun getLayerCached(cacheKey: String): Drawable? = layerCache.get(cacheKey)

    fun putLayerInCache(
        cacheKey: String,
        drawable: Drawable,
    ) {
        layerCache.put(cacheKey, drawable)
    }
}

// Sized by the decoded pixels the drawables hold, the minimum charge keeps small drawables like badges
// within the entry limit
private fun <T : Drawable> drawableCache(
    sizeKib: Int,
    maxEntries: Int,
): LruCache<String, T> = object : LruCache<String, T>(sizeKib) {
    override fun sizeOf(
        key: String,
        value: T,
    ): Int {
        val minimumEntryCharge = (sizeKib / maxEntries).toLong()
        return (value.estimatedBytes() / BYTES_PER_KIB).coerceIn(minimumEntryCharge, Int.MAX_VALUE.toLong()).toInt()
    }
}

private fun Drawable.estimatedBytes(): Long = when (this) {
    is ClockedAnimationDrawable -> source.estimatedBytes() + frameBufferBytes

    is LayerDrawable -> (0 until numberOfLayers).sumOf { getDrawable(it).estimatedBytes() }

    else -> {
        val pixels = intrinsicWidth.coerceAtLeast(1).toLong() * intrinsicHeight.coerceAtLeast(1)
        val buffers =
            when (this) {
                is Animatable -> ANIMATED_DRAWABLE_BUFFER_COUNT
                else -> 1L
            }
        pixels * BYTES_PER_PIXEL * buffers
    }
}

val LocalEmoteAnimationCoordinator =
    staticCompositionLocalOf<EmoteAnimationCoordinator> {
        error("No EmoteAnimationCoordinator provided. Wrap your chat composables with CompositionLocalProvider.")
    }

val LocalChatPageVisible = compositionLocalOf { true }
