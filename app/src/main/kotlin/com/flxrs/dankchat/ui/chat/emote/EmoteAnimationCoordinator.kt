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
import com.flxrs.dankchat.utils.extensions.forEachLayer
import com.flxrs.dankchat.utils.extensions.setRunning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

@Single
@Stable
class EmoteAnimationCoordinator(
    private val chatSettingsDataStore: ChatSettingsDataStore,
    dispatchersProvider: DispatchersProvider,
) {
    private val emoteCache = LruCache<String, Drawable>(512)
    private val layerCache = LruCache<String, LayerDrawable>(256)
    private val dimensionCache = LruCache<String, Pair<Int, Int>>(1024)

    // Stacks with failed layers render only what loaded and retry on their next occurrence,
    // rows size them by these until the complete stack replaces them
    private val partialDimensionCache = LruCache<String, Pair<Int, Int>>(256)

    // Bumped on every put so rows waiting for emote dimensions can re-check and upgrade
    // to the measured text fast path
    private val _dimensionUpdates = MutableStateFlow(0L)
    val dimensionUpdates: StateFlow<Long> = _dimensionUpdates.asStateFlow()

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
            _dimensionUpdates.update { it + 1 }
        }
    }

    fun getPartialDimensions(key: String): Pair<Int, Int>? = partialDimensionCache.get(key)

    fun putPartialDimensions(
        key: String,
        dimensions: Pair<Int, Int>,
    ) {
        val previous = partialDimensionCache.put(key, dimensions)
        if (previous != dimensions) {
            _dimensionUpdates.update { it + 1 }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val invalidationListeners = HashMap<Drawable, MutableSet<() -> Unit>>()

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
            .launchIn(CoroutineScope(SupervisorJob() + dispatchersProvider.immediate))
    }

    fun registerInvalidationListener(
        drawable: Drawable,
        listener: () -> Unit,
    ) {
        val listeners = invalidationListeners.getOrPut(drawable) { mutableSetOf() }
        listeners += listener
        drawable.callback = fanOutCallback
        if (listeners.size == 1) {
            // Starting on the first visible occurrence phase-aligns emotes that enter the
            // viewport together, additional occurrences must not reset the running animation
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
            // an explicit stop is the only thing that halts it while the drawable stays cached
            drawable.setAnimationsRunning(false)
            drawable.callback = null
        }
    }

    private fun Drawable.setAnimationsRunning(running: Boolean) {
        when (this) {
            is LayerDrawable -> forEachLayer<Animatable> { it.setRunning(running) }
            is Animatable -> setRunning(running)
        }
    }

    fun getCached(url: String): Drawable? = emoteCache.get(url)

    fun putInCache(
        url: String,
        drawable: Drawable,
    ) {
        emoteCache.put(url, drawable)
    }

    fun getLayerCached(cacheKey: String): LayerDrawable? = layerCache.get(cacheKey)

    fun putLayerInCache(
        cacheKey: String,
        layerDrawable: LayerDrawable,
    ) {
        layerCache.put(cacheKey, layerDrawable)
    }
}

val LocalEmoteAnimationCoordinator =
    staticCompositionLocalOf<EmoteAnimationCoordinator> {
        error("No EmoteAnimationCoordinator provided. Wrap your chat composables with CompositionLocalProvider.")
    }

val LocalChatPageVisible = compositionLocalOf { true }
