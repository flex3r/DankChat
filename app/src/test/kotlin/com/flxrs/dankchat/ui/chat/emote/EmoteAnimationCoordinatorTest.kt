package com.flxrs.dankchat.ui.chat.emote

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.flxrs.dankchat.di.DispatchersProvider
import com.flxrs.dankchat.preferences.chat.ChatSettings
import com.flxrs.dankchat.preferences.chat.ChatSettingsDataStore
import com.flxrs.dankchat.preferences.developer.DeveloperSettings
import com.flxrs.dankchat.preferences.developer.DeveloperSettingsDataStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EmoteAnimationCoordinatorTest {
    private val chatSettingsDataStore: ChatSettingsDataStore =
        mockk {
            every { currentSettings } returns MutableStateFlow(ChatSettings())
        }
    private val dispatchersProvider =
        object : DispatchersProvider {
            override val default: CoroutineDispatcher = Dispatchers.Main.immediate
            override val io: CoroutineDispatcher = Dispatchers.Main.immediate
            override val main: CoroutineDispatcher = Dispatchers.Main.immediate
            override val immediate: CoroutineDispatcher = Dispatchers.Main.immediate
        }
    private val developerSettingsDataStore: DeveloperSettingsDataStore =
        mockk {
            every { current() } returns DeveloperSettings()
        }
    private val coordinator = EmoteAnimationCoordinator(chatSettingsDataStore, dispatchersProvider, developerSettingsDataStore)

    @Test
    fun `emits once on collection and again only for watched keys`() = runTest(UnconfinedTestDispatcher()) {
        var emissions = 0
        val job = launch { coordinator.dimensionChanges(setOf(WATCHED_KEY)).collect { emissions++ } }
        assertEquals(1, emissions)

        coordinator.putDimensions(OTHER_KEY, 10 to 10)
        assertEquals(1, emissions)

        coordinator.putDimensions(WATCHED_KEY, 10 to 10)
        assertEquals(2, emissions)

        coordinator.putPartialDimensions(WATCHED_KEY, 20 to 20)
        assertEquals(3, emissions)
        job.cancel()
    }

    @Test
    fun `unchanged dimensions do not emit`() = runTest(UnconfinedTestDispatcher()) {
        var emissions = 0
        coordinator.putDimensions(WATCHED_KEY, 10 to 10)
        val job = launch { coordinator.dimensionChanges(setOf(WATCHED_KEY)).collect { emissions++ } }

        coordinator.putDimensions(WATCHED_KEY, 10 to 10)
        assertEquals(1, emissions)
        job.cancel()
    }

    @Test
    fun `cancelled collectors stop listening`() = runTest(UnconfinedTestDispatcher()) {
        var emissions = 0
        val job = launch { coordinator.dimensionChanges(setOf(WATCHED_KEY)).collect { emissions++ } }
        job.cancel()

        coordinator.putDimensions(WATCHED_KEY, 10 to 10)
        assertEquals(1, emissions)
    }

    @Test
    fun `concurrent loads of the same key share one load`() = runTest(UnconfinedTestDispatcher()) {
        var loads = 0
        val gate = CompletableDeferred<Drawable>()
        val load: suspend () -> Drawable? = {
            loads++
            gate.await()
        }
        val first = async { coordinator.loadShared(WATCHED_KEY, load) }
        val second = async { coordinator.loadShared(WATCHED_KEY, load) }
        val drawable = ColorDrawable(Color.RED)
        gate.complete(drawable)

        assertSame(drawable, first.await())
        assertSame(drawable, second.await())
        assertEquals(1, loads)
    }

    @Test
    fun `a cancelled caller does not cancel the shared load`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<Drawable>()
        val first = launch { coordinator.loadShared(WATCHED_KEY) { gate.await() } }
        val second = async { coordinator.loadShared(WATCHED_KEY) { gate.await() } }
        first.cancel()
        val drawable = ColorDrawable(Color.RED)
        gate.complete(drawable)

        assertSame(drawable, second.await())
    }

    @Test
    fun `a finished load is not reused by later calls`() = runTest(UnconfinedTestDispatcher()) {
        var loads = 0
        coordinator.loadShared(WATCHED_KEY) { ColorDrawable(Color.RED).also { loads++ } }
        coordinator.loadShared(WATCHED_KEY) { ColorDrawable(Color.RED).also { loads++ } }

        assertEquals(2, loads)
    }

    private companion object {
        const val WATCHED_KEY = "https://cdn.7tv.app/emote/1/4x.webp\n49"
        const val OTHER_KEY = "https://cdn.7tv.app/emote/2/4x.webp\n49"
    }
}
